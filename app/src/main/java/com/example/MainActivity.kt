package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.LocalPreferencesStore
import com.example.data.local.SmartSheetDatabase
import com.example.data.remote.GoogleDriveSheetsClient
import com.example.data.remote.GoogleWorkspaceAuthManager
import com.example.data.repository.AuthRepository
import com.example.data.repository.SheetSyncRepository
import com.example.data.repository.WorkspaceRepository
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.ActivityLogsScreen
import com.example.ui.screens.AddRecordScreen
import com.example.ui.screens.AdminHubScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EmployeeDetailScreen
import com.example.ui.screens.EmployeesScreen
import com.example.ui.screens.GlobalSearchScreen
import com.example.ui.screens.LegalDocumentScreen
import com.example.ui.screens.LegalDocumentType
import com.example.ui.screens.PermissionsMatrixScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RecordAuditsScreen
import com.example.ui.screens.RecordDetailScreen
import com.example.ui.screens.RecordsBreakdownScreen
import com.example.ui.screens.SearchHistoryDetailScreen
import com.example.ui.screens.SheetsManagementScreen
import com.example.ui.screens.SyncDiagnosticsScreen
import com.example.ui.screens.WorksheetsDetailScreen
import com.example.ui.screens.WorkspaceSettingsScreen
import com.example.ui.theme.SmartSheetTheme
import com.example.util.AppLinksConfig
import com.example.viewmodel.AuthSessionState
import com.example.viewmodel.AuthViewModel
import com.google.firebase.Firebase
import com.example.viewmodel.MainWorkspaceViewModel
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

  private lateinit var authViewModelRef: AuthViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val databaseId = getString(R.string.firestore_database_id)
    val firestore = FirebaseFirestore.getInstance(databaseId)
    val firebaseAuth = Firebase.auth
    val prefs = LocalPreferencesStore(applicationContext)
    val authRepo = AuthRepository(db = firestore, auth = firebaseAuth)
    val credentialManager = CredentialManager.create(applicationContext)

    setContent {
      val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
          initializer { AuthViewModel(authRepo, prefs) }
        }
      )
      authViewModelRef = authViewModel

      val darkOverride by authViewModel.darkThemeOverride.collectAsStateWithLifecycle()
      val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
      val inviteCodeInput by authViewModel.inviteCodeInput.collectAsStateWithLifecycle()
      val isSigningIn by authViewModel.isSigningIn.collectAsStateWithLifecycle()
      val isDeletingAccount by authViewModel.isDeletingAccount.collectAsStateWithLifecycle()
      val authError by authViewModel.authError.collectAsStateWithLifecycle()
      var unauthLegalDoc by remember { mutableStateOf<LegalDocumentType?>(null) }

      LaunchedEffect(Unit) {
        extractInviteCodeFromIntent(intent)?.let { code ->
          authViewModel.setIncomingInviteCode(code, resolveIfSignedIn = true)
        }
        authViewModel.attemptAutoSignIn(this@MainActivity, credentialManager)
      }

      SmartSheetTheme(darkTheme = darkOverride ?: androidx.compose.foundation.isSystemInDarkTheme()) {
        val activePublicDoc = unauthLegalDoc
        if (activePublicDoc != null &&
          (sessionState is AuthSessionState.SignedOut || sessionState is AuthSessionState.Blocked)
        ) {
          LegalDocumentScreen(
            documentType = activePublicDoc,
            onBack = { unauthLegalDoc = null },
            onOpenOtherDocument = { unauthLegalDoc = it },
          )
        } else {
          when (val state = sessionState) {
            AuthSessionState.CheckingSession,
            AuthSessionState.ResolvingProfile -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  CircularProgressIndicator()
                  Spacer(Modifier.height(12.dp))
                  Text(
                    text =
                      if (state == AuthSessionState.CheckingSession) "Restoring Smart Sheet session..."
                      else "Loading workspace profile...",
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
              }
            }

            AuthSessionState.SignedOut -> {
              AuthScreen(
                inviteCodeInput = inviteCodeInput,
                onInviteCodeChange = { authViewModel.setIncomingInviteCode(it) },
                isSigningIn = isSigningIn,
                authError = authError,
                isBlockedState = false,
                onSignInClicked = { authViewModel.signInWithGoogle(this@MainActivity, credentialManager) },
                onRetryClicked = { authViewModel.retryProfileResolution() },
                onSignOutClicked = { authViewModel.signOut(credentialManager) },
                onOpenLegalDocument = { unauthLegalDoc = it },
              )
            }

            is AuthSessionState.Blocked -> {
              AuthScreen(
                inviteCodeInput = inviteCodeInput,
                onInviteCodeChange = { authViewModel.setIncomingInviteCode(it) },
                isSigningIn = isSigningIn,
                authError = state.reason,
                isBlockedState = true,
                onSignInClicked = { authViewModel.signInWithGoogle(this@MainActivity, credentialManager) },
                onRetryClicked = { authViewModel.retryProfileResolution() },
                onSignOutClicked = { authViewModel.signOut(credentialManager) },
                onOpenLegalDocument = { unauthLegalDoc = it },
              )
            }

            is AuthSessionState.Authenticated -> {
              val profile = state.profile
              key(profile.userId) {
                AuthenticatedWorkspaceShell(
                  profile = profile,
                  firestore = firestore,
                  authRepository = authRepo,
                  darkThemeOverride = darkOverride,
                  isDeletingAccount = isDeletingAccount,
                  onSetDarkThemeOverride = { authViewModel.setDarkThemeOverride(it) },
                  onDeleteAccount = { preCleanup, onError ->
                    authViewModel.deleteAccount(
                      credentialManager = credentialManager,
                      workspaceId = profile.workspaceId,
                      isAdmin = profile.resolvedRole() == UserRole.ADMIN,
                      onCleanupBeforeDelete = preCleanup,
                      onResult = { res ->
                        res.exceptionOrNull()?.let { err ->
                          onError(err.message ?: "Account deletion failed")
                        }
                      },
                    )
                  },
                  onSignOut = { authViewModel.signOut(credentialManager) },
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val code = extractInviteCodeFromIntent(intent)
    if (code != null && ::authViewModelRef.isInitialized) {
      authViewModelRef.setIncomingInviteCode(code, resolveIfSignedIn = true)
    }
  }

  private fun extractInviteCodeFromIntent(intent: Intent?): String? {
    val data: Uri = intent?.data ?: return null
    return AppLinksConfig.extractInviteCodeFromUri(data)
  }
}

private data class NavDestinationItem(
  val route: AppRoute,
  val label: String,
  val icon: ImageVector,
  val testTag: String,
)

@Composable
private fun AuthenticatedWorkspaceShell(
  profile: UserProfile,
  firestore: FirebaseFirestore,
  authRepository: AuthRepository,
  darkThemeOverride: Boolean?,
  isDeletingAccount: Boolean,
  onSetDarkThemeOverride: (Boolean?) -> Unit,
  onDeleteAccount: (preCleanup: suspend () -> Unit, onError: (String) -> Unit) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val activity = context as ComponentActivity
  val database = remember { SmartSheetDatabase.getInstance(context.applicationContext) }
  val workspaceRepository = remember(firestore) { WorkspaceRepository(firestore) }
  val driveSheetsClient = remember { GoogleDriveSheetsClient() }
  val googleAuthManager = remember { GoogleWorkspaceAuthManager() }
  val syncRepository = remember(firestore, database, driveSheetsClient, workspaceRepository) {
    SheetSyncRepository(firestore, database.cachedSheetRowDao(), driveSheetsClient, workspaceRepository)
  }

  val workspaceViewModel: MainWorkspaceViewModel = viewModel(
    key = profile.userId,
    factory = viewModelFactory {
      initializer {
        MainWorkspaceViewModel(
          userProfile = profile,
          workspaceRepository = workspaceRepository,
          syncRepository = syncRepository,
          rowDao = database.cachedSheetRowDao(),
          driveSheetsClient = driveSheetsClient,
          googleAuthManager = googleAuthManager,
          authRepository = authRepository,
        )
      }
    },
  )

  val oauthLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
      workspaceViewModel.handleGoogleAuthResult(activity, result.resultCode, result.data)
    }

  val triggerGoogleOAuth: () -> Unit = {
    workspaceViewModel.requestGoogleWorkspaceAuth(
      activity = activity,
      onResolutionRequired = { sender ->
        oauthLauncher.launch(IntentSenderRequest.Builder(sender).build())
      },
    )
  }

  val handleSignOut: () -> Unit = {
    workspaceViewModel.clearLocalGoogleAuthSession()
    onSignOut()
  }

  LaunchedEffect(profile.userId) {
    workspaceViewModel.bindHostActivity(activity)
    workspaceViewModel.attemptSilentGoogleRefresh(activity)
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(workspaceViewModel) {
    workspaceViewModel.toastMessages.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination
  val isAdmin = profile.resolvedRole() == UserRole.ADMIN

  val topNavItems =
    if (isAdmin) {
      listOf(
        NavDestinationItem(AppRoute.Dashboard, "Home", Icons.Default.Dashboard, "nav_home"),
        NavDestinationItem(AppRoute.GlobalSearch(), "Search", Icons.Default.Search, "nav_search"),
        NavDestinationItem(AppRoute.AddRecord(), "Add", Icons.Default.AddCircle, "nav_add"),
        NavDestinationItem(AppRoute.SheetsManagement, "Sheets", Icons.Default.TableChart, "nav_sheets"),
        NavDestinationItem(AppRoute.AdminHub, "Admin", Icons.Default.AdminPanelSettings, "nav_admin"),
      )
    } else {
      listOf(
        NavDestinationItem(AppRoute.Dashboard, "Home", Icons.Default.Dashboard, "nav_home"),
        NavDestinationItem(AppRoute.GlobalSearch(), "Search", Icons.Default.Search, "nav_search"),
        NavDestinationItem(AppRoute.AddRecord(), "Add", Icons.Default.AddCircle, "nav_add"),
        NavDestinationItem(AppRoute.Profile, "Account", Icons.Default.Person, "nav_profile"),
      )
    }

  val navigateTopLevel: (AppRoute) -> Unit = { dest ->
    navController.navigate(dest) {
      popUpTo<AppRoute.Dashboard> { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            topNavItems.forEach { item ->
              val selected = currentDest?.hasRoute(item.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopLevel(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            topNavItems.forEach { item ->
              val selected = currentDest?.hasRoute(item.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopLevel(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.testTag(item.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = AppRoute.Dashboard,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
          composable<AppRoute.Dashboard> {
            DashboardScreen(
              viewModel = workspaceViewModel,
              onNavigateToSearch = { q ->
                navController.navigate(AppRoute.GlobalSearch(initialQuery = q))
              },
              onNavigateToRecordDetail = { id ->
                navController.navigate(AppRoute.RecordDetail(id))
              },
              onNavigateToAddRecord = {
                navController.navigate(AppRoute.AddRecord())
              },
              onNavigateToSheets = {
                if (isAdmin) {
                  navController.navigate(AppRoute.SheetsManagement)
                } else {
                  navController.navigate(AppRoute.RecordsBreakdownDetail)
                }
              },
              onNavigateToWorksheetsDetail = {
                navController.navigate(AppRoute.WorksheetsDetail)
              },
              onNavigateToRecordsBreakdown = {
                navController.navigate(AppRoute.RecordsBreakdownDetail)
              },
              onNavigateToEmployees = {
                if (isAdmin) navController.navigate(AppRoute.Employees)
              },
              onNavigateToSearchHistory = {
                navController.navigate(AppRoute.SearchHistoryDetail)
              },
              onNavigateToRecordAudits = {
                navController.navigate(AppRoute.RecordAuditsDetail)
              },
              onNavigateToSyncDiagnostics = {
                navController.navigate(AppRoute.SyncDiagnosticsDetail)
              },
              onNavigateToActivityLogs = {
                navController.navigate(AppRoute.ActivityLogs)
              },
              onNavigateToAnalytics = {
                if (isAdmin) navController.navigate(AppRoute.Analytics)
              },
              onConnectGoogleAuthClick = triggerGoogleOAuth,
            )
          }

          composable<AppRoute.GlobalSearch> { entry ->
            val args = entry.toRoute<AppRoute.GlobalSearch>()
            GlobalSearchScreen(
              viewModel = workspaceViewModel,
              initialQuery = args.initialQuery,
              filterSheetId = args.filterSheetId,
              onOpenRecordDetail = { id ->
                navController.navigate(AppRoute.RecordDetail(id))
              },
              onConnectGoogleAuthClick = triggerGoogleOAuth,
            )
          }

          composable<AppRoute.RecordDetail> { entry ->
            val args = entry.toRoute<AppRoute.RecordDetail>()
            RecordDetailScreen(
              recordId = args.recordId,
              viewModel = workspaceViewModel,
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.AddRecord> { entry ->
            val args = entry.toRoute<AppRoute.AddRecord>()
            AddRecordScreen(
              viewModel = workspaceViewModel,
              preselectedSheetId = args.preselectedSheetId,
              onConnectGoogleAuthClick = triggerGoogleOAuth,
              onNavigateToSheets = {
                if (isAdmin) navController.navigate(AppRoute.SheetsManagement)
              },
              onRecordAdded = {
                navController.navigate(AppRoute.GlobalSearch()) {
                  launchSingleTop = true
                }
              },
            )
          }

          composable<AppRoute.SheetsManagement> {
            SheetsManagementScreen(
              viewModel = workspaceViewModel,
              onConnectGoogleAuthClick = triggerGoogleOAuth,
              onOpenSheetInSearch = { sheetId ->
                navController.navigate(AppRoute.GlobalSearch(filterSheetId = sheetId))
              },
            )
          }

          composable<AppRoute.AdminHub> {
            AdminHubScreen(
              viewModel = workspaceViewModel,
              onNavigateToEmployees = { navController.navigate(AppRoute.Employees) },
              onNavigateToPermissionsMatrix = { navController.navigate(AppRoute.PermissionsMatrix) },
              onNavigateToAnalytics = { navController.navigate(AppRoute.Analytics) },
              onNavigateToActivityLogs = { navController.navigate(AppRoute.ActivityLogs) },
              onNavigateToSyncDiagnostics = { navController.navigate(AppRoute.SyncDiagnosticsDetail) },
              onNavigateToSettings = { navController.navigate(AppRoute.Settings) },
              onNavigateToProfile = { navController.navigate(AppRoute.Profile) },
              onSignOut = handleSignOut,
            )
          }

          composable<AppRoute.Employees> {
            EmployeesScreen(
              viewModel = workspaceViewModel,
              onOpenEmployeeDetail = { empUid ->
                navController.navigate(AppRoute.EmployeeDetail(empUid))
              },
              onOpenPermissionsMatrix = {
                navController.navigate(AppRoute.PermissionsMatrix)
              },
            )
          }

          composable<AppRoute.EmployeeDetail> { entry ->
            val args = entry.toRoute<AppRoute.EmployeeDetail>()
            EmployeeDetailScreen(
              employeeUid = args.employeeUid,
              viewModel = workspaceViewModel,
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.PermissionsMatrix> {
            PermissionsMatrixScreen(
              viewModel = workspaceViewModel,
              onBack = { navController.popBackStack() },
              onOpenEmployeeDetail = { empUid ->
                navController.navigate(AppRoute.EmployeeDetail(empUid))
              },
            )
          }

          composable<AppRoute.Analytics> {
            AnalyticsScreen(
              viewModel = workspaceViewModel,
              onNavigateToSheets = { navController.navigate(AppRoute.SheetsManagement) },
              onNavigateToEmployees = { navController.navigate(AppRoute.Employees) },
              onNavigateToSyncDiagnostics = { navController.navigate(AppRoute.SyncDiagnosticsDetail) },
              onNavigateToActivityLogs = { navController.navigate(AppRoute.ActivityLogs) },
              onNavigateToRecordAudits = { navController.navigate(AppRoute.RecordAuditsDetail) },
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.ActivityLogs> {
            ActivityLogsScreen(
              viewModel = workspaceViewModel,
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.Settings> {
            WorkspaceSettingsScreen(
              viewModel = workspaceViewModel,
              darkThemeOverride = darkThemeOverride,
              onSetDarkThemeOverride = onSetDarkThemeOverride,
              onSignOut = handleSignOut,
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.Profile> {
            ProfileScreen(
              viewModel = workspaceViewModel,
              darkThemeOverride = darkThemeOverride,
              onSetDarkThemeOverride = onSetDarkThemeOverride,
              isDeletingAccount = isDeletingAccount,
              onOpenLegalDocument = { doc ->
                navController.navigate(AppRoute.LegalDocument(doc.routeKey))
              },
              onDeleteAccount = {
                onDeleteAccount(
                  { workspaceViewModel.clearLocalDataBeforeDeletion(activity) },
                  { msg -> workspaceViewModel.emitToast(msg) },
                )
              },
              onSignOut = handleSignOut,
            )
          }

          composable<AppRoute.WorksheetsDetail> {
            WorksheetsDetailScreen(
              viewModel = workspaceViewModel,
              onOpenWorksheetInSearch = { sheetId, _ ->
                navController.navigate(AppRoute.GlobalSearch(filterSheetId = sheetId))
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.RecordsBreakdownDetail> {
            RecordsBreakdownScreen(
              viewModel = workspaceViewModel,
              onOpenSheetInSearch = { sheetId ->
                navController.navigate(AppRoute.GlobalSearch(filterSheetId = sheetId))
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.SyncDiagnosticsDetail> {
            SyncDiagnosticsScreen(
              viewModel = workspaceViewModel,
              onConnectGoogleAuthClick = triggerGoogleOAuth,
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.SearchHistoryDetail> {
            SearchHistoryDetailScreen(
              viewModel = workspaceViewModel,
              onRunSearch = { q ->
                workspaceViewModel.updateSearchQuery(q)
                navController.navigate(AppRoute.GlobalSearch(initialQuery = q))
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.RecordAuditsDetail> {
            RecordAuditsScreen(
              viewModel = workspaceViewModel,
              onBack = { navController.popBackStack() },
            )
          }

          composable<AppRoute.LegalDocument> { entry ->
            val args = entry.toRoute<AppRoute.LegalDocument>()
            LegalDocumentScreen(
              documentType = LegalDocumentType.fromKey(args.documentType),
              onBack = { navController.popBackStack() },
              onOpenOtherDocument = { other ->
                navController.navigate(AppRoute.LegalDocument(other.routeKey))
              },
            )
          }
        }
      }
    }
  }
}
