package com.example.viewmodel

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CachedSheetRowDao
import com.example.data.local.CachedSheetRowEntity
import com.example.data.remote.GoogleApiException
import com.example.data.remote.GoogleDriveSheetsClient
import com.example.data.remote.GoogleWorkspaceAuthManager
import com.example.data.remote.SilentRefreshOutcome
import com.example.data.repository.AuthRepository
import com.example.data.repository.SheetSyncRepository
import com.example.data.repository.WorkspaceRepository
import com.example.model.AccountStatus
import com.example.model.ActivityLog
import com.example.model.DriveSpreadsheetItem
import com.example.model.EmployeeRecord
import com.example.model.Invitation
import com.example.model.RecordAudit
import com.example.model.RemoteSpreadsheetPreview
import com.example.model.SavedSearch
import com.example.model.SearchFilters
import com.example.model.SheetConnection
import com.example.model.SheetPermissionLevel
import com.example.model.SheetRecord
import com.example.model.SyncLog
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.WorksheetMetadata
import com.example.model.WorkspaceSettings
import com.example.util.AnalyticsEngine
import com.example.util.AnalyticsSummary
import com.example.util.SearchEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainWorkspaceViewModel(
  val userProfile: UserProfile,
  private val workspaceRepository: WorkspaceRepository,
  private val syncRepository: SheetSyncRepository,
  private val rowDao: CachedSheetRowDao,
  private val driveSheetsClient: GoogleDriveSheetsClient,
  private val googleAuthManager: GoogleWorkspaceAuthManager,
  private val authRepository: AuthRepository? = null,
) : ViewModel() {

  val isAdmin: Boolean = userProfile.resolvedRole() == UserRole.ADMIN
  val workspaceId: String = userProfile.workspaceId
  val currentUserId: String = userProfile.userId

  val isGoogleAuthorized: StateFlow<Boolean> = googleAuthManager.isAuthorized
  val googleAccessToken: StateFlow<String?> = googleAuthManager.accessToken
  val connectedGoogleEmail: StateFlow<String?> = googleAuthManager.connectedEmail
  val googleAuthError: StateFlow<String?> = googleAuthManager.lastAuthError

  private var hostActivityRef: WeakReference<Activity>? = null
  private val syncMutex = Mutex()
  private val pendingWriteMutex = Mutex()
  private var lastDriveQueryKey: String? = null

  private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val toastMessages = _toastMessages.asSharedFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  private val _syncProgressLabel = MutableStateFlow<String?>(null)
  val syncProgressLabel: StateFlow<String?> = _syncProgressLabel.asStateFlow()

  val sheetsState: StateFlow<UiState<List<SheetConnection>>> =
    workspaceRepository
      .observeSheets(workspaceId, isAdmin, currentUserId)
      .asUiStateFlow("Failed to load connected spreadsheets")

  val worksheetsState: StateFlow<UiState<List<WorksheetMetadata>>> =
    workspaceRepository
      .observeWorksheets(workspaceId, isAdmin, currentUserId)
      .asUiStateFlow("Failed to load worksheets")

  val employeesState: StateFlow<UiState<List<EmployeeRecord>>> =
    if (isAdmin) {
      workspaceRepository
        .observeEmployees(workspaceId)
        .asUiStateFlow("Failed to load employees")
    } else {
      MutableStateFlow(UiState.Success(emptyList()))
    }

  val myEmployeeRecord: StateFlow<EmployeeRecord?> =
    if (!isAdmin) {
      workspaceRepository
        .observeMyEmployeeRecord(workspaceId, currentUserId)
        .catch { err ->
          if (err is CancellationException) throw err
          emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)
    } else {
      MutableStateFlow(null)
    }

  val invitationsState: StateFlow<UiState<List<Invitation>>> =
    if (isAdmin) {
      workspaceRepository
        .observeInvitations(currentUserId)
        .asUiStateFlow("Failed to load invitations")
    } else {
      MutableStateFlow(UiState.Success(emptyList()))
    }

  val activityLogsState: StateFlow<UiState<List<ActivityLog>>> =
    workspaceRepository
      .observeActivityLogs(workspaceId, isAdmin, currentUserId)
      .asUiStateFlow("Failed to load activity logs")

  val recordAuditsState: StateFlow<UiState<List<RecordAudit>>> =
    workspaceRepository
      .observeRecordAudits(workspaceId, isAdmin, currentUserId)
      .asUiStateFlow("Failed to load record audits")

  val syncLogsState: StateFlow<UiState<List<SyncLog>>> =
    workspaceRepository
      .observeSyncLogs(workspaceId, isAdmin, currentUserId)
      .asUiStateFlow("Failed to load synchronization logs")

  val settingsState: StateFlow<WorkspaceSettings> =
    workspaceRepository
      .observeSettings(workspaceId)
      .catch { err ->
        if (err is CancellationException) throw err
        emit(WorkspaceSettings(workspaceId = workspaceId))
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        WorkspaceSettings(workspaceId = workspaceId),
      )

  val savedSearchesState: StateFlow<List<SavedSearch>> =
    workspaceRepository
      .observeSavedSearches(currentUserId)
      .catch { err ->
        if (err is CancellationException) throw err
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val cachedRows: StateFlow<List<CachedSheetRowEntity>> =
    combine(sheetsState, myEmployeeRecord) { state, emp ->
        val sheets = (state as? UiState.Success)?.data.orEmpty()
        if (isAdmin) {
          sheets.map { it.id }
        } else if (emp == null || emp.resolvedStatus() != AccountStatus.ACTIVE) {
          emptyList()
        } else {
          val allowedSet = emp.assignedSheetIds.toSet()
          sheets.filter { it.active && it.id in allowedSet }.map { it.id }
        }
      }
      .distinctUntilChanged()
      .flatMapLatest { activeIds ->
        if (isAdmin) {
          rowDao.observeRowsForWorkspace(workspaceId)
        } else if (activeIds.isEmpty()) {
          flowOf(emptyList())
        } else {
          rowDao.observeRowsForSheets(workspaceId, activeIds)
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  // --- GLOBAL LIVE SEARCH STATE (OFFLOADED TO Dispatchers.Default) ---
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _searchFilters = MutableStateFlow(SearchFilters())
  val searchFilters: StateFlow<SearchFilters> = _searchFilters.asStateFlow()

  private val _searchResultsLimit = MutableStateFlow(40)
  val searchResultsLimit: StateFlow<Int> = _searchResultsLimit.asStateFlow()

  private val debouncedQuery: Flow<String> =
    _searchQuery.debounce(180L).distinctUntilChanged()

  val searchResults: StateFlow<List<SheetRecord>> =
    combine(cachedRows, debouncedQuery, _searchFilters, recordAuditsState) {
        rows,
        query,
        filters,
        auditsUi ->
        val audits = (auditsUi as? UiState.Success)?.data.orEmpty()
        SearchEngine.searchAndFilter(rows, query, filters, audits)
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val searchSuggestions: StateFlow<List<String>> =
    combine(cachedRows, debouncedQuery) { rows, q -> SearchEngine.buildSuggestions(rows, q) }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  // --- ANALYTICS STATE (OFFLOADED TO Dispatchers.Default) ---
  private val _analyticsWindowDays = MutableStateFlow(7)
  val analyticsWindowDays: StateFlow<Int> = _analyticsWindowDays.asStateFlow()

  val analyticsSummary: StateFlow<AnalyticsSummary> =
    combine(cachedRows, recordAuditsState, sheetsState, _analyticsWindowDays) {
        rows,
        auditsUi,
        sheetsUi,
        windowDays ->
        val audits = (auditsUi as? UiState.Success)?.data.orEmpty()
        val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
        AnalyticsEngine.computeAnalyticsSummary(
          rows = rows,
          audits = audits,
          sheets = sheets,
          windowDays = windowDays,
        )
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AnalyticsSummary())

  // --- DRIVE BROWSER & SHEET PICKER STATE ---
  private val _driveFiles = MutableStateFlow<List<DriveSpreadsheetItem>>(emptyList())
  val driveFiles: StateFlow<List<DriveSpreadsheetItem>> = _driveFiles.asStateFlow()

  private val _isLoadingDrive = MutableStateFlow(false)
  val isLoadingDrive: StateFlow<Boolean> = _isLoadingDrive.asStateFlow()

  private val _sheetPreview = MutableStateFlow<RemoteSpreadsheetPreview?>(null)
  val sheetPreview: StateFlow<RemoteSpreadsheetPreview?> = _sheetPreview.asStateFlow()

  // --- SELECTED RECORD DETAILS ---
  private val _selectedRecordId = MutableStateFlow<String?>(null)
  val selectedRecord: StateFlow<SheetRecord?> =
    combine(searchResults, _selectedRecordId, cachedRows, recordAuditsState) {
        results,
        selectedId,
        rows,
        auditsUi ->
        if (selectedId == null) return@combine null
        results.firstOrNull { it.id == selectedId }
          ?: rows.firstOrNull { it.id == selectedId }?.let { entity ->
            val audits = (auditsUi as? UiState.Success)?.data.orEmpty()
            val map =
              audits.associateBy { "${it.sheetConnectionId}|${it.worksheetTitle}|${it.rowNumber}" }
            SearchEngine.entityToRecord(entity, map)
          }
      }
      .flowOn(Dispatchers.Default)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  init {
    startBackgroundAutoSyncLoop()
    observeImmediateAccessAndCloudSync()
  }

  private fun startBackgroundAutoSyncLoop() {
    if (!isAdmin) return
    viewModelScope.launch {
      while (isActive) {
        val intervalMinutes = settingsState.value.autoSyncIntervalMinutes.coerceIn(5L, 1440L)
        delay(intervalMinutes * 60_000L)
        val token = googleAccessToken.value
        if (!token.isNullOrBlank() && !_isSyncing.value) {
          syncAllActiveSheets(silent = true)
        }
      }
    }
  }

  private fun observeImmediateAccessAndCloudSync() {
    if (!isAdmin) {
      // 1. Immediately purge unauthorized Room data or reconcile newly granted sheet memberships
      viewModelScope.launch {
        myEmployeeRecord
          .distinctUntilChanged { old, new ->
            old?.resolvedStatus() == new?.resolvedStatus() &&
              old?.assignedSheetIds == new?.assignedSheetIds
          }
          .collectLatest { emp ->
            if (emp == null || emp.resolvedStatus() != AccountStatus.ACTIVE) {
              syncRepository.purgeUnauthorizedSheetsFromLocalCache(workspaceId, emptyList())
            } else {
              syncRepository.purgeUnauthorizedSheetsFromLocalCache(workspaceId, emp.assignedSheetIds)
              if (emp.assignedSheetIds.isNotEmpty()) {
                authRepository?.reconcileEmployeeSheetMemberships(
                  workspaceId = workspaceId,
                  employeeUid = currentUserId,
                  assignedSheetIds = emp.assignedSheetIds,
                )
              }
            }
          }
      }

      // 2. Automatically sync assigned sheet row_pages from Firestore into Room for instant search/view
      viewModelScope.launch {
        combine(sheetsState, myEmployeeRecord) { state, emp ->
            val sheets = (state as? UiState.Success)?.data.orEmpty()
            if (emp == null || emp.resolvedStatus() != AccountStatus.ACTIVE) {
              emptyList()
            } else {
              val allowed = emp.assignedSheetIds.toSet()
              sheets.filter { it.active && it.id in allowed }
            }
          }
          .distinctUntilChanged { old, new -> old.map { it.id } == new.map { it.id } }
          .flatMapLatest { assignedSheets ->
            if (assignedSheets.isEmpty()) {
              flowOf(emptyList())
            } else {
              val perSheetFlows =
                assignedSheets.map { sheet ->
                  workspaceRepository.observeSheetRowPages(workspaceId, sheet.id, currentUserId).map {
                    pages ->
                    sheet to pages
                  }
                }
              combine(perSheetFlows) { arr -> arr.toList() }
            }
          }
          .collectLatest { pairs ->
            for ((sheet, pages) in pairs) {
              syncRepository.syncEmployeeRowPagesToRoom(workspaceId, sheet, pages)
            }
          }
      }
    } else {
      // Admin flushes authorized employee pending_writes to Google Sheets automatically when connected
      viewModelScope.launch {
        combine(sheetsState, googleAccessToken) { state, token ->
            val sheets = (state as? UiState.Success)?.data.orEmpty().filter { it.active }
            sheets to token
          }
          .flatMapLatest { (activeSheets, token) ->
            if (token.isNullOrBlank() || activeSheets.isEmpty()) {
              flowOf(emptyList())
            } else {
              val perSheetFlows =
                activeSheets.map { sheet ->
                  workspaceRepository
                    .observePendingWritesForSheet(workspaceId, sheet.id, currentUserId)
                    .map { writes -> sheet to writes }
                }
              combine(perSheetFlows) { arr -> arr.toList() }
            }
          }
          .collectLatest { sheetWrites ->
            val token = googleAccessToken.value ?: return@collectLatest
            val allWorksheets = (worksheetsState.value as? UiState.Success)?.data.orEmpty()
            pendingWriteMutex.withLock {
              for ((sheet, writes) in sheetWrites) {
                for (pw in writes) {
                  val ws =
                    allWorksheets.firstOrNull {
                      it.sheetConnectionId == sheet.id &&
                        it.worksheetTitle.equals(pw.worksheetTitle, ignoreCase = true)
                    }
                  syncRepository.processPendingWriteAsAdmin(
                    admin = userProfile,
                    sheet = sheet,
                    worksheet = ws,
                    pendingWrite = pw,
                    accessToken = token,
                  )
                }
              }
            }
          }
      }
    }
  }

  fun setAnalyticsWindowDays(days: Int) {
    _analyticsWindowDays.value = days.coerceIn(3, 30)
  }

  fun updateSearchQuery(q: String) {
    _searchQuery.value = q
    _searchResultsLimit.value = 40
  }

  fun updateSearchFilters(filters: SearchFilters) {
    _searchFilters.value = filters
    _searchResultsLimit.value = 40
  }

  fun clearSearchFilters() {
    _searchFilters.value = SearchFilters()
  }

  fun loadMoreSearchResults() {
    _searchResultsLimit.value += 40
  }

  fun selectRecord(recordId: String?) {
    _selectedRecordId.value = recordId
  }

  fun recordSearchHistory(saved: Boolean = false) {
    val q = _searchQuery.value.trim()
    if (q.isBlank()) return
    val count = searchResults.value.size.toLong()
    val summary = _searchFilters.value.summary()
    viewModelScope.launch {
      workspaceRepository.saveOrRecordSearch(userProfile, q, summary, count, saved)
      if (saved) emitToast("Saved search '$q'")
    }
  }

  fun deleteSavedSearch(searchId: String) {
    viewModelScope.launch {
      workspaceRepository.deleteSavedSearch(currentUserId, searchId)
    }
  }

  // --- GOOGLE OAUTH & DRIVE/SHEETS ACTIONS ---
  fun bindHostActivity(activity: Activity) {
    hostActivityRef = WeakReference(activity)
  }

  fun attemptSilentGoogleRefresh(activity: Activity) {
    hostActivityRef = WeakReference(activity)
    if (!isAdmin) return
    viewModelScope.launch {
      when (
        googleAuthManager.refreshAccessToken(
          activity = activity,
          expectedEmail = userProfile.email,
        )
      ) {
        is SilentRefreshOutcome.TokenReady -> {}
        is SilentRefreshOutcome.NeedsUserConsent -> {}
        is SilentRefreshOutcome.Failed -> {}
      }
    }
  }

  fun requestGoogleWorkspaceAuth(
    activity: Activity,
    onResolutionRequired: (IntentSender) -> Unit,
    onAuthorized: () -> Unit = {},
  ) {
    hostActivityRef = WeakReference(activity)
    googleAuthManager.requestWorkspaceAuthorization(
      activity = activity,
      expectedEmail = userProfile.email,
      onResolutionRequired = onResolutionRequired,
      onAuthorized = {
        emitToast("Google Drive & Sheets connected (${userProfile.email})")
        onAuthorized()
      },
      onError = { err -> emitToast(err) },
    )
  }

  fun handleGoogleAuthResult(
    activity: Activity,
    resultCode: Int,
    data: Intent?,
    onAuthorized: () -> Unit = {},
  ) {
    hostActivityRef = WeakReference(activity)
    googleAuthManager.handleAuthorizationResult(
      activity = activity,
      resultCode = resultCode,
      data = data,
      expectedEmail = userProfile.email,
      onAuthorized = {
        emitToast("Google Drive & Sheets connected (${userProfile.email})")
        onAuthorized()
      },
      onError = { err -> emitToast(err) },
    )
  }

  fun disconnectGoogleWorkspace(activity: Activity) {
    hostActivityRef = WeakReference(activity)
    viewModelScope.launch {
      driveSheetsClient.invalidateCache()
      val res = googleAuthManager.revokeGoogleAccess(activity, userProfile.email)
      _driveFiles.value = emptyList()
      _sheetPreview.value = null
      if (res.isSuccess) {
        emitToast("Disconnected Google Drive & Sheets authorization")
      } else {
        emitToast("Cleared local Google Drive & Sheets session")
      }
    }
  }

  fun clearLocalGoogleAuthSession() {
    googleAuthManager.clearToken()
    _driveFiles.value = emptyList()
    _sheetPreview.value = null
  }

  suspend fun clearLocalDataBeforeDeletion(activity: Activity) {
    runCatching { driveSheetsClient.invalidateCache() }
    runCatching { googleAuthManager.revokeGoogleAccess(activity, userProfile.email) }
    runCatching { rowDao.clearWorkspace(workspaceId) }
    _driveFiles.value = emptyList()
    _sheetPreview.value = null
  }

  private suspend fun <T> withValidGoogleToken(
    silentOnAuthRequired: Boolean = false,
    block: suspend (token: String) -> Result<T>,
  ): Result<T> {
    var token = googleAccessToken.value
    val activity = hostActivityRef?.get()

    if (token.isNullOrBlank()) {
      if (activity != null) {
        when (val refresh = googleAuthManager.refreshAccessToken(activity, userProfile.email)) {
          is SilentRefreshOutcome.TokenReady -> token = refresh.accessToken
          is SilentRefreshOutcome.NeedsUserConsent -> {
            val msg = "Authorize Google Drive & Sheets access first."
            if (!silentOnAuthRequired) emitToast(msg)
            return Result.failure(IllegalStateException(msg))
          }
          is SilentRefreshOutcome.Failed -> {
            if (!silentOnAuthRequired) emitToast(refresh.userMessage)
            return Result.failure(IllegalStateException(refresh.userMessage))
          }
        }
      } else {
        val msg = "Authorize Google Drive & Sheets access first."
        if (!silentOnAuthRequired) emitToast(msg)
        return Result.failure(IllegalStateException(msg))
      }
    }

    val firstResult = block(token)
    val err = firstResult.exceptionOrNull()
    if (firstResult.isFailure && err is GoogleApiException && err.httpCode == 401 && activity != null) {
      when (
        val refreshed =
          googleAuthManager.refreshAccessToken(
            activity = activity,
            expectedEmail = userProfile.email,
            staleTokenToClear = token,
          )
      ) {
        is SilentRefreshOutcome.TokenReady -> {
          return block(refreshed.accessToken)
        }
        is SilentRefreshOutcome.NeedsUserConsent -> {
          val msg = "Google authorization expired or was revoked. Tap Connect Google to re-authorize."
          if (!silentOnAuthRequired) emitToast(msg)
          return Result.failure(IllegalStateException(msg))
        }
        is SilentRefreshOutcome.Failed -> {
          if (!silentOnAuthRequired) emitToast(refreshed.userMessage)
          return Result.failure(IllegalStateException(refreshed.userMessage))
        }
      }
    }
    return firstResult
  }

  fun loadDriveSpreadsheets(searchQuery: String = "", forceRefresh: Boolean = false) {
    val cleanQuery = searchQuery.trim()
    if (_isLoadingDrive.value && !forceRefresh && lastDriveQueryKey == cleanQuery) {
      return
    }
    lastDriveQueryKey = cleanQuery
    viewModelScope.launch {
      _isLoadingDrive.value = true
      val res = withValidGoogleToken { token ->
        driveSheetsClient.listSpreadsheets(
          accessToken = token,
          searchQuery = cleanQuery,
          forceRefresh = forceRefresh,
        )
      }
      _isLoadingDrive.value = false
      if (res.isSuccess) {
        _driveFiles.value = res.getOrThrow()
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Failed to load Google Drive spreadsheets.")
      }
    }
  }

  fun createNewSpreadsheet(
    title: String,
    initialTabTitle: String,
    initialHeaders: List<String>,
    onComplete: () -> Unit,
  ) {
    viewModelScope.launch {
      _isSyncing.value = true
      _syncProgressLabel.value = "Creating Google Sheet..."
      val res = withValidGoogleToken { token ->
        syncRepository.createNewSpreadsheet(
          admin = userProfile,
          title = title,
          initialWorksheetTitle = initialTabTitle,
          initialHeaders = initialHeaders,
          accessToken = token,
        )
      }
      _isSyncing.value = false
      _syncProgressLabel.value = null
      if (res.isSuccess) {
        val created = res.getOrThrow()
        emitToast("Created & connected '${created.customName}' in your Google Drive")
        loadDriveSpreadsheets(forceRefresh = true)
        onComplete()
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Failed to create Google Spreadsheet.")
      }
    }
  }

  fun inspectSpreadsheet(spreadsheetIdOrUrl: String) {
    if (spreadsheetIdOrUrl.isBlank()) {
      emitToast("Enter a Spreadsheet ID or Google Sheets URL.")
      return
    }
    viewModelScope.launch {
      _isLoadingDrive.value = true
      val res = withValidGoogleToken { token ->
        driveSheetsClient.getSpreadsheetMetadata(token, spreadsheetIdOrUrl)
      }
      _isLoadingDrive.value = false
      if (res.isSuccess) {
        _sheetPreview.value = res.getOrThrow()
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Unable to open spreadsheet.")
      }
    }
  }

  fun clearSheetPreview() {
    _sheetPreview.value = null
  }

  fun connectSpreadsheet(
    preview: RemoteSpreadsheetPreview,
    customName: String,
    selectedWorksheets: List<String>,
    onComplete: () -> Unit,
  ) {
    viewModelScope.launch {
      _isSyncing.value = true
      _syncProgressLabel.value = "Connecting ${customName.ifBlank { preview.title }}..."
      val res =
        syncRepository.connectSpreadsheet(
          admin = userProfile,
          preview = preview,
          customName = customName,
          selectedWorksheetTitles = selectedWorksheets,
          accessToken = googleAccessToken.value,
        )
      _isSyncing.value = false
      _syncProgressLabel.value = null
      if (res.isSuccess) {
        _sheetPreview.value = null
        emitToast("Connected '${customName.ifBlank { preview.title }}'")
        onComplete()
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Failed to connect spreadsheet")
      }
    }
  }

  fun syncSheet(sheet: SheetConnection) {
    if (!isAdmin && googleAccessToken.value.isNullOrBlank()) {
      emitToast("Workspace sync is managed live from your Admin's connection.")
      return
    }
    viewModelScope.launch {
      if (!syncMutex.tryLock()) {
        emitToast("Synchronization already in progress...")
        return@launch
      }
      try {
        _isSyncing.value = true
        _syncProgressLabel.value = "Syncing ${sheet.customName}..."
        val res = withValidGoogleToken { token ->
          syncRepository.syncSheet(userProfile, sheet, token)
        }
        if (res.isSuccess) {
          emitToast("Synced ${res.getOrThrow()} rows from ${sheet.customName}")
        } else {
          emitToast(res.exceptionOrNull()?.message ?: "Sync failed for ${sheet.customName}")
        }
      } finally {
        _isSyncing.value = false
        _syncProgressLabel.value = null
        syncMutex.unlock()
      }
    }
  }

  fun syncAllActiveSheets(silent: Boolean = false) {
    val sheets = (sheetsState.value as? UiState.Success)?.data.orEmpty().filter { it.active }
    if (!isAdmin && googleAccessToken.value.isNullOrBlank()) {
      viewModelScope.launch {
        val emp = myEmployeeRecord.value
        if (emp != null && emp.resolvedStatus() == AccountStatus.ACTIVE) {
          authRepository?.reconcileEmployeeSheetMemberships(
            workspaceId = workspaceId,
            employeeUid = currentUserId,
            assignedSheetIds = emp.assignedSheetIds,
          )
        }
        if (!silent) {
          emitToast("Refreshed assigned sheets (${sheets.size} active)")
        }
      }
      return
    }
    if (sheets.isEmpty()) {
      if (!silent) emitToast("No active sheets to synchronize.")
      return
    }
    viewModelScope.launch {
      if (!syncMutex.tryLock()) {
        if (!silent) emitToast("Synchronization already running...")
        return@launch
      }
      try {
        _isSyncing.value = true
        var syncedTotal = 0L
        var failures = 0
        var lastErr: String? = null
        for (sheet in sheets) {
          _syncProgressLabel.value = "Syncing ${sheet.customName}..."
          val res = withValidGoogleToken(silentOnAuthRequired = silent) { token ->
            syncRepository.syncSheet(userProfile, sheet, token)
          }
          if (res.isSuccess) {
            syncedTotal += res.getOrThrow()
          } else {
            failures++
            lastErr = res.exceptionOrNull()?.message
          }
        }
        if (!silent) {
          if (failures == 0) {
            emitToast("Synchronized $syncedTotal records across ${sheets.size} sheets")
          } else {
            emitToast(lastErr ?: "Synced $syncedTotal records ($failures sheet errors)")
          }
        }
      } finally {
        _isSyncing.value = false
        _syncProgressLabel.value = null
        syncMutex.unlock()
      }
    }
  }

  fun updateSheetSettings(
    sheet: SheetConnection,
    customName: String,
    active: Boolean,
    selectedWorksheets: List<String>,
    aadhaarMaskingEnabled: Boolean,
  ) {
    viewModelScope.launch {
      val res =
        syncRepository.updateSheetSettings(
          admin = userProfile,
          sheet = sheet,
          customName = customName,
          active = active,
          selectedWorksheets = selectedWorksheets,
          aadhaarMaskingEnabled = aadhaarMaskingEnabled,
        )
      if (res.isSuccess) emitToast("Updated '${customName.trim()}'")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to update sheet")
    }
  }

  fun disconnectSheet(sheet: SheetConnection) {
    val worksheets = (worksheetsState.value as? UiState.Success)?.data.orEmpty()
    viewModelScope.launch {
      val res = syncRepository.disconnectSheet(userProfile, sheet, worksheets)
      if (res.isSuccess) emitToast("Disconnected '${sheet.customName}'")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to disconnect sheet")
    }
  }

  fun updateWorksheetValidationConfig(
    worksheet: WorksheetMetadata,
    requiredFields: List<String>,
    fieldTypes: Map<String, String>,
    dropdownOptions: Map<String, String>,
  ) {
    viewModelScope.launch {
      val res =
        syncRepository.updateWorksheetValidationConfig(
          admin = userProfile,
          worksheet = worksheet,
          requiredFields = requiredFields,
          fieldTypes = fieldTypes,
          dropdownOptions = dropdownOptions,
        )
      if (res.isSuccess) emitToast("Saved form rules for ${worksheet.worksheetTitle}")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to save form rules")
    }
  }

  // --- RECORD INSERT, EDIT & DELETE ---
  suspend fun checkDuplicateImei(imei: String): List<CachedSheetRowEntity> {
    if (!settingsState.value.duplicateImeiCheckEnabled) return emptyList()
    return syncRepository.findDuplicateImei(workspaceId, imei)
  }

  fun submitNewRecord(
    sheet: SheetConnection,
    worksheet: WorksheetMetadata,
    orderedValues: List<String>,
    onSuccess: (Int) -> Unit,
  ) {
    if (!canAddToSheet(sheet.id)) {
      emitToast("You do not have permission to add records to '${sheet.customName}'.")
      return
    }
    viewModelScope.launch {
      _isSyncing.value = true
      if (!isAdmin && googleAccessToken.value.isNullOrBlank()) {
        _syncProgressLabel.value = "Submitting record via workspace queue..."
        val queueRes =
          syncRepository.submitPendingSheetWrite(
            employee = userProfile,
            sheet = sheet,
            worksheetTitle = worksheet.worksheetTitle,
            actionType = "ADD",
            rowNumber = 0,
            headers = worksheet.headers,
            values = orderedValues,
          )
        _isSyncing.value = false
        _syncProgressLabel.value = null
        if (queueRes.isSuccess) {
          emitToast("Submitted new row to '${sheet.customName}' sync queue")
          onSuccess(0)
        } else {
          emitToast(queueRes.exceptionOrNull()?.message ?: "Failed to submit record")
        }
        return@launch
      }

      _syncProgressLabel.value = "Inserting record to Google Sheets..."
      val res = withValidGoogleToken { token ->
        syncRepository.insertRecordToSheet(
          user = userProfile,
          sheet = sheet,
          worksheet = worksheet,
          orderedValues = orderedValues,
          accessToken = token,
        )
      }
      _isSyncing.value = false
      _syncProgressLabel.value = null
      if (res.isSuccess) {
        val rowNum = res.getOrThrow()
        emitToast("Inserted row #$rowNum into ${worksheet.worksheetTitle}")
        onSuccess(rowNum)
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Failed to insert record")
      }
    }
  }

  fun updateExistingRecord(
    record: SheetRecord,
    sheet: SheetConnection,
    orderedValues: List<String>,
    onSuccess: () -> Unit,
  ) {
    if (!canEditSheet(sheet.id)) {
      emitToast("You do not have permission to edit records in '${sheet.customName}'.")
      return
    }
    viewModelScope.launch {
      _isSyncing.value = true
      if (!isAdmin && googleAccessToken.value.isNullOrBlank()) {
        _syncProgressLabel.value = "Submitting edit via workspace queue..."
        val queueRes =
          syncRepository.submitPendingSheetWrite(
            employee = userProfile,
            sheet = sheet,
            worksheetTitle = record.worksheetTitle,
            actionType = "EDIT",
            rowNumber = record.rowNumber,
            headers = record.headers,
            values = orderedValues,
          )
        _isSyncing.value = false
        _syncProgressLabel.value = null
        if (queueRes.isSuccess) {
          emitToast("Submitted edit for row #${record.rowNumber} to sync queue")
          onSuccess()
        } else {
          emitToast(queueRes.exceptionOrNull()?.message ?: "Failed to submit edit")
        }
        return@launch
      }

      _syncProgressLabel.value = "Updating row #${record.rowNumber}..."
      val res = withValidGoogleToken { token ->
        syncRepository.updateRecordInSheet(
          user = userProfile,
          sheet = sheet,
          worksheetTitle = record.worksheetTitle,
          rowNumber = record.rowNumber,
          headers = record.headers,
          orderedValues = orderedValues,
          accessToken = token,
        )
      }
      _isSyncing.value = false
      _syncProgressLabel.value = null
      if (res.isSuccess) {
        emitToast("Updated row #${record.rowNumber}")
        onSuccess()
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Failed to update record")
      }
    }
  }

  fun deleteExistingRecord(
    record: SheetRecord,
    sheet: SheetConnection,
    onSuccess: () -> Unit,
  ) {
    if (!canEditSheet(sheet.id)) {
      emitToast("You do not have permission to delete records in '${sheet.customName}'.")
      return
    }
    val worksheet =
      (worksheetsState.value as? UiState.Success)?.data?.firstOrNull {
        it.sheetConnectionId == sheet.id &&
          it.worksheetTitle.equals(record.worksheetTitle, ignoreCase = true)
      }
    viewModelScope.launch {
      _isSyncing.value = true
      if (!isAdmin && googleAccessToken.value.isNullOrBlank()) {
        _syncProgressLabel.value = "Submitting delete via workspace queue..."
        val queueRes =
          syncRepository.submitPendingSheetWrite(
            employee = userProfile,
            sheet = sheet,
            worksheetTitle = record.worksheetTitle,
            actionType = "DELETE",
            rowNumber = record.rowNumber,
            headers = record.headers,
            values = listOf(record.customerName.ifBlank { record.mobileNumber }),
          )
        _isSyncing.value = false
        _syncProgressLabel.value = null
        if (queueRes.isSuccess) {
          emitToast("Submitted deletion for row #${record.rowNumber} to sync queue")
          onSuccess()
        } else {
          emitToast(queueRes.exceptionOrNull()?.message ?: "Failed to submit deletion")
        }
        return@launch
      }

      _syncProgressLabel.value = "Deleting row #${record.rowNumber}..."
      val res = withValidGoogleToken { token ->
        syncRepository.deleteRecordFromSheet(
          user = userProfile,
          sheet = sheet,
          worksheet = worksheet,
          worksheetTitle = record.worksheetTitle,
          rowNumber = record.rowNumber,
          recordSummary = record.customerName.ifBlank { record.mobileNumber },
          accessToken = token,
        )
      }
      _isSyncing.value = false
      _syncProgressLabel.value = null
      if (res.isSuccess) {
        emitToast("Deleted row #${record.rowNumber} from ${record.worksheetTitle}")
        onSuccess()
      } else {
        emitToast(res.exceptionOrNull()?.message ?: "Failed to delete record")
      }
    }
  }

  // --- EMPLOYEE & INVITATION MANAGEMENT ---
  fun createInvitation(
    targetEmail: String,
    defaultPermission: SheetPermissionLevel,
    assignedSheetIds: List<String>,
    sheetPermissions: Map<String, String> = emptyMap(),
    maxUses: Long = 1L,
    expiryDays: Int? = 7,
    onError: (String) -> Unit = {},
    onCreated: (Invitation) -> Unit = {},
  ) {
    val allSheets = (sheetsState.value as? UiState.Success)?.data.orEmpty()
    val allWorksheets = (worksheetsState.value as? UiState.Success)?.data.orEmpty()
    viewModelScope.launch {
      val res =
        workspaceRepository.createInvitation(
          admin = userProfile,
          targetEmail = targetEmail,
          defaultPermission = defaultPermission,
          assignedSheetIds = assignedSheetIds,
          sheetPermissions = sheetPermissions,
          maxUses = maxUses,
          expiryDays = expiryDays,
          allSheets = allSheets,
          allWorksheets = allWorksheets,
        )
      if (res.isSuccess) {
        val inv = res.getOrThrow()
        emitToast("Sent employee invitation to ${inv.targetEmail ?: inv.inviteCode}")
        onCreated(inv)
      } else {
        val errMsg = res.exceptionOrNull()?.message ?: "Failed to create invitation"
        emitToast(errMsg)
        onError(errMsg)
      }
    }
  }

  fun revokeInvitation(inviteCode: String) {
    viewModelScope.launch {
      val res = workspaceRepository.revokeInvitation(userProfile, inviteCode)
      if (res.isSuccess) emitToast("Revoked invitation $inviteCode")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to revoke invitation")
    }
  }

  fun updateEmployeeAccess(
    employee: EmployeeRecord,
    newStatus: AccountStatus,
    newDefaultPermission: SheetPermissionLevel,
    newAssignedSheetIds: List<String>,
    newSheetPermissions: Map<String, String>,
  ) {
    val allSheets = (sheetsState.value as? UiState.Success)?.data.orEmpty()
    val allWorksheets = (worksheetsState.value as? UiState.Success)?.data.orEmpty()
    viewModelScope.launch {
      val res =
        workspaceRepository.updateEmployeeAccess(
          admin = userProfile,
          employee = employee,
          newStatus = newStatus,
          newDefaultPermission = newDefaultPermission,
          newAssignedSheetIds = newAssignedSheetIds,
          newSheetPermissions = newSheetPermissions,
          allSheets = allSheets,
          allWorksheets = allWorksheets,
        )
      if (res.isSuccess) emitToast("Updated access for ${employee.name}")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to update employee")
    }
  }

  fun updateEmployeeProfileDetails(employeeUid: String, name: String, email: String) {
    viewModelScope.launch {
      val res = workspaceRepository.updateEmployeeProfileDetails(userProfile, employeeUid, name, email)
      if (res.isSuccess) emitToast("Updated employee details")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to update employee details")
    }
  }

  fun removeEmployee(employee: EmployeeRecord) {
    val allSheets = (sheetsState.value as? UiState.Success)?.data.orEmpty()
    val allWorksheets = (worksheetsState.value as? UiState.Success)?.data.orEmpty()
    viewModelScope.launch {
      val res = workspaceRepository.removeEmployee(userProfile, employee, allSheets, allWorksheets)
      if (res.isSuccess) emitToast("Removed access for ${employee.name}")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to remove employee")
    }
  }

  fun saveWorkspaceSettings(settings: WorkspaceSettings) {
    viewModelScope.launch {
      val res = workspaceRepository.saveWorkspaceSettings(userProfile, settings)
      if (res.isSuccess) emitToast("Workspace settings saved")
      else emitToast(res.exceptionOrNull()?.message ?: "Failed to save settings")
    }
  }

  // --- ROLE & PERMISSION HELPERS ---
  fun permissionForSheet(sheetId: String): SheetPermissionLevel {
    if (isAdmin) return SheetPermissionLevel.FULL_ACCESS
    val emp = myEmployeeRecord.value ?: return SheetPermissionLevel.SEARCH_ONLY
    return emp.permissionForSheet(sheetId)
  }

  fun canAddToSheet(sheetId: String): Boolean {
    if (isAdmin) return true
    val emp = myEmployeeRecord.value ?: return false
    if (emp.resolvedStatus() != AccountStatus.ACTIVE) return false
    return sheetId in emp.assignedSheetIds && emp.permissionForSheet(sheetId).canAdd()
  }

  fun canEditSheet(sheetId: String): Boolean {
    if (isAdmin) return true
    val emp = myEmployeeRecord.value ?: return false
    if (emp.resolvedStatus() != AccountStatus.ACTIVE) return false
    return sheetId in emp.assignedSheetIds && emp.permissionForSheet(sheetId).canEdit()
  }

  fun canExportData(): Boolean {
    return isAdmin || settingsState.value.allowEmployeeExport
  }

  fun canViewFullAadhaar(): Boolean = isAdmin

  fun emitToast(message: String) {
    _toastMessages.tryEmit(message)
  }

  private fun <T> Flow<T>.asUiStateFlow(fallbackError: String): StateFlow<UiState<T>> =
    this.map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        emit(UiState.Error(rootError.message ?: fallbackError))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )
}
