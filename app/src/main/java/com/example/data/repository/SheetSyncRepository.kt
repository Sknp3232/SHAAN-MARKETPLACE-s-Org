package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.CachedSheetRowDao
import com.example.data.local.CachedSheetRowEntity
import com.example.data.remote.GoogleDriveSheetsClient
import com.example.model.ConnectionHealth
import com.example.model.FieldInputType
import com.example.model.PendingSheetWrite
import com.example.model.RemoteSpreadsheetPreview
import com.example.model.SheetConnection
import com.example.model.SheetRowPage
import com.example.model.SyncStatus
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.WorksheetMetadata
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SheetSyncRepository(
  private val db: FirebaseFirestore,
  private val rowDao: CachedSheetRowDao,
  private val sheetsClient: GoogleDriveSheetsClient,
  private val workspaceRepository: WorkspaceRepository,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context,
    rowDao: CachedSheetRowDao,
    sheetsClient: GoogleDriveSheetsClient,
    workspaceRepository: WorkspaceRepository,
  ) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    rowDao = rowDao,
    sheetsClient = sheetsClient,
    workspaceRepository = workspaceRepository,
  )

  private val sheetSyncMutexes = ConcurrentHashMap<String, Mutex>()

  private fun mutexForSheet(sheetId: String): Mutex =
    sheetSyncMutexes.getOrPut(sheetId) { Mutex() }

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in before modifying sheets.")

  suspend fun connectSpreadsheet(
    admin: UserProfile,
    preview: RemoteSpreadsheetPreview,
    customName: String,
    selectedWorksheetTitles: List<String>,
    accessToken: String?,
  ): Result<SheetConnection> {
    val workspaceId = admin.workspaceId
    val sheetDocId =
      "sh_${preview.spreadsheetId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(90)}"
    val sheetPath = "workspaces/$workspaceId/sheets/$sheetDocId"

    return try {
      val uid = requireUserId()
      val batch = db.batch()
      val sheetRef =
        db.collection("workspaces").document(workspaceId).collection("sheets").document(sheetDocId)
      val cleanCustomName = customName.trim().ifBlank { preview.title }.take(200)
      val allWorksheetNames = preview.worksheets.map { it.title }.take(50)
      val chosenWorksheets =
        selectedWorksheetTitles.ifEmpty { listOfNotNull(allWorksheetNames.firstOrNull()) }.take(50)

      batch.set(
        sheetRef,
        mapOf(
          "workspaceId" to workspaceId,
          "spreadsheetId" to preview.spreadsheetId,
          "originalTitle" to preview.title.take(200),
          "customName" to cleanCustomName,
          "spreadsheetUrl" to preview.spreadsheetUrl.takeIf { it.isNotBlank() }?.take(500),
          "selectedWorksheets" to chosenWorksheets,
          "allWorksheets" to allWorksheetNames,
          "active" to true,
          "connectionHealth" to ConnectionHealth.SYNCING.name,
          "totalRecords" to 0L,
          "aadhaarMaskingEnabled" to true,
          "assignedEmployeeUids" to emptyList<String>(),
          "createdByUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null },
      )

      for (ws in preview.worksheets.filter { it.title in chosenWorksheets }) {
        val wsDocId = buildWorksheetDocId(sheetDocId, ws.title)
        val wsRef =
          db.collection("workspaces")
            .document(workspaceId)
            .collection("worksheets")
            .document(wsDocId)
        batch.set(
          wsRef,
          mapOf(
            "workspaceId" to workspaceId,
            "sheetConnectionId" to sheetDocId,
            "spreadsheetId" to preview.spreadsheetId,
            "worksheetTitle" to ws.title.take(150),
            "sheetGid" to ws.sheetId,
            "headers" to emptyList<String>(),
            "requiredFields" to emptyList<String>(),
            "fieldTypes" to emptyMap<String, String>(),
            "dropdownOptions" to emptyMap<String, String>(),
            "recordCount" to 0L,
            "assignedEmployeeUids" to emptyList<String>(),
            "syncStatus" to SyncStatus.SYNCING.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()

      val connection =
        SheetConnection(
          id = sheetDocId,
          workspaceId = workspaceId,
          spreadsheetId = preview.spreadsheetId,
          originalTitle = preview.title,
          customName = cleanCustomName,
          spreadsheetUrl = preview.spreadsheetUrl,
          selectedWorksheets = chosenWorksheets,
          allWorksheets = allWorksheetNames,
          active = true,
          connectionHealth = ConnectionHealth.SYNCING.name,
          createdByUid = uid,
        )

      workspaceRepository.logActivity(
        user = admin,
        activityType = "SHEET_CONNECTED",
        description = "Connected spreadsheet '$cleanCustomName' (${chosenWorksheets.size} tabs)",
        sheetId = sheetDocId,
        sheetName = cleanCustomName,
      )

      if (!accessToken.isNullOrBlank()) {
        syncSheet(user = admin, sheet = connection, accessToken = accessToken)
      }

      Result.success(connection)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, sheetPath)
      Result.failure(e)
    }
  }

  suspend fun updateSheetSettings(
    admin: UserProfile,
    sheet: SheetConnection,
    customName: String,
    active: Boolean,
    selectedWorksheets: List<String>,
    aadhaarMaskingEnabled: Boolean,
  ): Result<Unit> {
    val path = "workspaces/${admin.workspaceId}/sheets/${sheet.id}"
    return try {
      requireUserId()
      val cleanSelected = selectedWorksheets.take(50)
      db.document(path)
        .update(
          mapOf(
            "customName" to customName.trim().ifBlank { sheet.originalTitle }.take(200),
            "active" to active,
            "selectedWorksheets" to cleanSelected,
            "aadhaarMaskingEnabled" to aadhaarMaskingEnabled,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      for (title in cleanSelected) {
        val wsDocId = buildWorksheetDocId(sheet.id, title)
        val wsRef =
          db.collection("workspaces")
            .document(admin.workspaceId)
            .collection("worksheets")
            .document(wsDocId)
        val snap = wsRef.get().await()
        if (!snap.exists()) {
          wsRef
            .set(
              mapOf(
                "workspaceId" to admin.workspaceId,
                "sheetConnectionId" to sheet.id,
                "spreadsheetId" to sheet.spreadsheetId,
                "worksheetTitle" to title.take(150),
                "sheetGid" to 0L,
                "headers" to emptyList<String>(),
                "requiredFields" to emptyList<String>(),
                "fieldTypes" to emptyMap<String, String>(),
                "dropdownOptions" to emptyMap<String, String>(),
                "recordCount" to 0L,
                "assignedEmployeeUids" to sheet.assignedEmployeeUids,
                "syncStatus" to SyncStatus.IDLE.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
      }

      workspaceRepository.logActivity(
        user = admin,
        activityType = "SHEET_UPDATED",
        description =
          "Updated sheet '${customName.trim()}': active=$active, worksheets=${cleanSelected.size}",
        sheetId = sheet.id,
        sheetName = customName.trim(),
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun disconnectSheet(
    admin: UserProfile,
    sheet: SheetConnection,
    worksheets: List<WorksheetMetadata>,
  ): Result<Unit> {
    val path = "workspaces/${admin.workspaceId}/sheets/${sheet.id}"
    return try {
      requireUserId()
      val sheetRef = db.document(path)
      // Remove row_pages and pending_writes subcollections cleanly before deleting sheet doc
      runCatching {
        sheetRef.collection("row_pages").get().await().documents.forEach { it.reference.delete().await() }
      }
      runCatching {
        sheetRef.collection("pending_writes").get().await().documents.forEach { it.reference.delete().await() }
      }
      val batch = db.batch()
      batch.delete(sheetRef)
      for (ws in worksheets.filter { it.sheetConnectionId == sheet.id }) {
        batch.delete(
          db.collection("workspaces")
            .document(admin.workspaceId)
            .collection("worksheets")
            .document(ws.id)
        )
      }
      batch.commit().await()
      rowDao.deleteBySheet(sheet.id)
      workspaceRepository.logActivity(
        user = admin,
        activityType = "SHEET_DISCONNECTED",
        description =
          "Disconnected '${sheet.customName}' from Smart Sheet (original Google Sheet preserved)",
        sheetId = sheet.id,
        sheetName = sheet.customName,
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun updateWorksheetValidationConfig(
    admin: UserProfile,
    worksheet: WorksheetMetadata,
    requiredFields: List<String>,
    fieldTypes: Map<String, String>,
    dropdownOptions: Map<String, String>,
  ): Result<Unit> {
    val path = "workspaces/${admin.workspaceId}/worksheets/${worksheet.id}"
    return try {
      requireUserId()
      db.document(path)
        .update(
          mapOf(
            "requiredFields" to requiredFields.take(100),
            "fieldTypes" to fieldTypes,
            "dropdownOptions" to dropdownOptions,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun syncSheet(
    user: UserProfile,
    sheet: SheetConnection,
    accessToken: String,
  ): Result<Long> =
    mutexForSheet(sheet.id).withLock {
      val startTime = System.currentTimeMillis()
      val workspaceId = user.workspaceId
      val sheetRef =
        db.collection("workspaces").document(workspaceId).collection("sheets").document(sheet.id)

      try {
        requireUserId()
        val tabs = sheet.selectedWorksheets
        if (tabs.isEmpty()) {
          return@withLock Result.success(0L)
        }

        val batchResult =
          sheetsClient.batchGetWorksheetValues(
            accessToken = accessToken,
            spreadsheetId = sheet.spreadsheetId,
            worksheetTitles = tabs,
          )
        if (batchResult.isFailure) {
          val errMsg = batchResult.exceptionOrNull()?.message ?: "Sync failed"
          val duration = System.currentTimeMillis() - startTime
          recordSyncFailure(user, sheet, errMsg, duration)
          return@withLock Result.failure(batchResult.exceptionOrNull() ?: Exception(errMsg))
        }

        val valuesMap = batchResult.getOrThrow()
        val nowMillis = System.currentTimeMillis()
        var totalSynced = 0L
        val publishedPageIds = HashSet<String>()

        for ((tabTitle, rawRows) in valuesMap) {
          val headers =
            rawRows.firstOrNull()?.mapIndexed { idx, h ->
              h.trim().ifBlank { "Column ${idx + 1}" }
            } ?: emptyList()

          val dataRows = if (rawRows.size > 1) rawRows.drop(1) else emptyList()
          val entities =
            withContext(Dispatchers.Default) {
              val list = ArrayList<CachedSheetRowEntity>(dataRows.size)
              dataRows.forEachIndexed { index, row ->
                if (!row.any { it.isNotBlank() }) return@forEachIndexed
                val rowNumber = index + 2
                val normalizedCells =
                  List(headers.size.coerceAtLeast(row.size)) { colIdx ->
                    row.getOrNull(colIdx)?.trim().orEmpty()
                  }
                list.add(
                  buildCachedEntity(
                    workspaceId = workspaceId,
                    sheetConnectionId = sheet.id,
                    spreadsheetId = sheet.spreadsheetId,
                    spreadsheetName = sheet.customName,
                    worksheetTitle = tabTitle,
                    rowNumber = rowNumber,
                    headers = headers,
                    cells = normalizedCells,
                    syncedAtMillis = nowMillis,
                    isFromCache = false,
                  )
                )
              }
              list
            }

          rowDao.replaceWorksheetRows(sheet.id, tabTitle, entities)
          totalSynced += entities.size

          if (user.resolvedRole() == UserRole.ADMIN) {
            val pageIds =
              publishWorksheetRowPages(
                workspaceId = workspaceId,
                sheet = sheet,
                tabTitle = tabTitle,
                headers = headers,
                entities = entities,
                syncedAtMillis = nowMillis,
              )
            publishedPageIds.addAll(pageIds)
          }

          val wsDocId = buildWorksheetDocId(sheet.id, tabTitle)
          val wsRef =
            db.collection("workspaces")
              .document(workspaceId)
              .collection("worksheets")
              .document(wsDocId)
          val existingWs = wsRef.get().await()
          if (existingWs.exists()) {
            if (user.resolvedRole() == UserRole.ADMIN) {
              val inferredTypes =
                headers.associateWith { h -> FieldInputType.inferFromHeader(h).name }
              val existingTypes =
                (existingWs.get("fieldTypes") as? Map<*, *>)
                  ?.mapNotNull { (k, v) -> (k as? String)?.let { ks -> (v as? String)?.let { vs -> ks to vs } } }
                  ?.toMap() ?: emptyMap()
              val mergedTypes = inferredTypes + existingTypes
              wsRef
                .update(
                  mapOf(
                    "headers" to headers.take(100),
                    "fieldTypes" to mergedTypes,
                    "recordCount" to entities.size.toLong(),
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "syncStatus" to SyncStatus.SUCCESS.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  )
                )
                .await()
            } else {
              wsRef
                .update(
                  mapOf(
                    "recordCount" to entities.size.toLong(),
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "syncStatus" to SyncStatus.SUCCESS.name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                  )
                )
                .await()
            }
          }
        }

        if (user.resolvedRole() == UserRole.ADMIN) {
          pruneStaleRowPages(workspaceId, sheet.id, publishedPageIds)
        }

        sheetRef
          .update(
            mapOf(
              "totalRecords" to totalSynced,
              "connectionHealth" to ConnectionHealth.HEALTHY.name,
              "lastSyncAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()

        val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        db.collection("workspaces")
          .document(workspaceId)
          .collection("sync_logs")
          .document()
          .set(
            mapOf(
              "workspaceId" to workspaceId,
              "sheetConnectionId" to sheet.id,
              "sheetName" to sheet.customName.take(200),
              "triggeredByUid" to user.userId,
              "triggeredByName" to user.displayName.take(120),
              "status" to "SUCCESS",
              "recordsSynced" to totalSynced,
              "durationMs" to duration,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()

        workspaceRepository.logActivity(
          user = user,
          activityType = "SHEET_SYNCED",
          description = "Synchronized $totalSynced records from '${sheet.customName}'",
          sheetId = sheet.id,
          sheetName = sheet.customName,
        )
        Result.success(totalSynced)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        recordSyncFailure(user, sheet, e.message ?: "Sync failed", duration)
        Result.failure(e)
      }
    }

  /**
   * Publishes chunked row snapshots to Firestore `row_pages` so invited employees can access
   * assigned sheet records immediately without requiring their own Google OAuth token or any
   * exposure of Admin OAuth credentials.
   */
  private suspend fun publishWorksheetRowPages(
    workspaceId: String,
    sheet: SheetConnection,
    tabTitle: String,
    headers: List<String>,
    entities: List<CachedSheetRowEntity>,
    syncedAtMillis: Long,
  ): List<String> {
    val pageIds = mutableListOf<String>()
    val pagesCollection =
      db.collection("workspaces")
        .document(workspaceId)
        .collection("sheets")
        .document(sheet.id)
        .collection("row_pages")

    val chunks = if (entities.isEmpty()) listOf(emptyList()) else entities.chunked(ROWS_PER_CLOUD_PAGE)
    val batches = mutableListOf(db.batch())
    var opsInBatch = 0

    chunks.forEachIndexed { pageIndex, chunk ->
      val pageId = buildRowPageDocId(sheet.id, tabTitle, pageIndex)
      pageIds.add(pageId)
      val compactRows =
        JSONArray().apply {
          for (entity in chunk) {
            val rowObj =
              JSONArray().apply {
                put(entity.rowNumber)
                put(JSONArray(entity.cellsJson))
              }
            put(rowObj)
          }
        }.toString()

      val payload =
        mapOf(
          "workspaceId" to workspaceId,
          "sheetConnectionId" to sheet.id,
          "spreadsheetId" to sheet.spreadsheetId,
          "spreadsheetName" to sheet.customName.take(200),
          "worksheetTitle" to tabTitle.take(150),
          "pageIndex" to pageIndex.toLong(),
          "headers" to headers.take(100),
          "rowsJson" to compactRows,
          "rowCount" to chunk.size.toLong(),
          "assignedEmployeeUids" to sheet.assignedEmployeeUids.take(100),
          "syncedAtMillis" to syncedAtMillis,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      batches.last().set(pagesCollection.document(pageId), payload)
      opsInBatch++
      if (opsInBatch >= 400) {
        batches.add(db.batch())
        opsInBatch = 0
      }
    }

    for (batch in batches) {
      if (opsInBatch > 0 || batches.size > 1) {
        batch.commit().await()
      }
    }
    return pageIds
  }

  private suspend fun pruneStaleRowPages(
    workspaceId: String,
    sheetId: String,
    validPageIds: Set<String>,
  ) {
    val pagesRef =
      db.collection("workspaces")
        .document(workspaceId)
        .collection("sheets")
        .document(sheetId)
        .collection("row_pages")
    val existingDocs = runCatching { pagesRef.get().await().documents }.getOrDefault(emptyList())
    val stale = existingDocs.filter { it.id !in validPageIds }
    if (stale.isEmpty()) return
    for (chunk in stale.chunked(400)) {
      val batch = db.batch()
      chunk.forEach { batch.delete(it.reference) }
      batch.commit().await()
    }
  }

  suspend fun syncEmployeeRowPagesToRoom(
    workspaceId: String,
    sheet: SheetConnection,
    pages: List<SheetRowPage>,
  ) {
    if (pages.isEmpty()) return
    val byTab = pages.groupBy { it.worksheetTitle }
    for ((tabTitle, tabPages) in byTab) {
      val entities =
        withContext(Dispatchers.Default) {
          val collected = ArrayList<CachedSheetRowEntity>()
          for (page in tabPages.sortedBy { it.pageIndex }) {
            val headers = page.headers
            val syncedAt = page.syncedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            val arr =
              try {
                JSONArray(page.rowsJson)
              } catch (_: Exception) {
                JSONArray()
              }
            for (i in 0 until arr.length()) {
              val entry = arr.optJSONArray(i) ?: continue
              val rowNumber = entry.optInt(0, i + 2)
              val cellsArr = entry.optJSONArray(1) ?: JSONArray()
              val cells = List(cellsArr.length()) { c -> cellsArr.optString(c, "") }
              collected.add(
                buildCachedEntity(
                  workspaceId = workspaceId,
                  sheetConnectionId = sheet.id,
                  spreadsheetId = sheet.spreadsheetId,
                  spreadsheetName = sheet.customName,
                  worksheetTitle = tabTitle,
                  rowNumber = rowNumber,
                  headers = headers,
                  cells = cells,
                  syncedAtMillis = syncedAt,
                  isFromCache = false,
                )
              )
            }
          }
          collected
        }
      rowDao.replaceWorksheetRows(sheet.id, tabTitle, entities)
    }
  }

  suspend fun purgeUnauthorizedSheetsFromLocalCache(
    workspaceId: String,
    allowedSheetIds: List<String>,
  ) {
    if (allowedSheetIds.isEmpty()) {
      rowDao.clearWorkspace(workspaceId)
    } else {
      rowDao.deleteRowsNotInSheets(workspaceId, allowedSheetIds)
    }
  }

  suspend fun submitPendingSheetWrite(
    employee: UserProfile,
    sheet: SheetConnection,
    worksheetTitle: String,
    actionType: String,
    rowNumber: Int,
    headers: List<String>,
    values: List<String>,
  ): Result<String> {
    val workspaceId = employee.workspaceId
    val writeId = "pw_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val path = "workspaces/$workspaceId/sheets/${sheet.id}/pending_writes/$writeId"
    return try {
      val uid = requireUserId()
      val payload =
        mapOf(
          "workspaceId" to workspaceId,
          "sheetConnectionId" to sheet.id,
          "spreadsheetId" to sheet.spreadsheetId,
          "worksheetTitle" to worksheetTitle.take(150),
          "actionType" to actionType,
          "rowNumber" to rowNumber.toLong(),
          "values" to values.take(100),
          "headers" to headers.take(100),
          "requestedByUid" to uid,
          "requestedByName" to employee.displayName.take(120),
          "requestedByEmail" to employee.email.take(200),
          "status" to "PENDING",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      db.document(path).set(payload).await()
      workspaceRepository.logActivity(
        user = employee,
        activityType = "PENDING_WRITE_${actionType}",
        description = "Queued $actionType change for '${sheet.customName}' / $worksheetTitle",
        sheetId = sheet.id,
        sheetName = sheet.customName,
        worksheetTitle = worksheetTitle,
        rowReference = if (rowNumber >= 2) "Row $rowNumber" else null,
      )
      Result.success(writeId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun processPendingWriteAsAdmin(
    admin: UserProfile,
    sheet: SheetConnection,
    worksheet: WorksheetMetadata?,
    pendingWrite: PendingSheetWrite,
    accessToken: String,
  ): Result<Unit> {
    val workspaceId = admin.workspaceId
    val writeRef =
      db.collection("workspaces")
        .document(workspaceId)
        .collection("sheets")
        .document(sheet.id)
        .collection("pending_writes")
        .document(pendingWrite.id)

    return try {
      val effectiveUser =
        admin.copy(
          userId = pendingWrite.requestedByUid,
          displayName = pendingWrite.requestedByName,
          email = pendingWrite.requestedByEmail,
        )
      val actionResult: Result<Any> =
        when (pendingWrite.actionType) {
          "ADD" -> {
            val ws =
              worksheet
                ?: WorksheetMetadata(
                  id = buildWorksheetDocId(sheet.id, pendingWrite.worksheetTitle),
                  workspaceId = workspaceId,
                  sheetConnectionId = sheet.id,
                  spreadsheetId = sheet.spreadsheetId,
                  worksheetTitle = pendingWrite.worksheetTitle,
                  headers = pendingWrite.headers,
                )
            insertRecordToSheet(
              user = admin,
              sheet = sheet,
              worksheet = ws,
              orderedValues = pendingWrite.values,
              accessToken = accessToken,
              attributionOverride = effectiveUser,
            )
          }
          "EDIT" ->
            updateRecordInSheet(
              user = admin,
              sheet = sheet,
              worksheetTitle = pendingWrite.worksheetTitle,
              rowNumber = pendingWrite.rowNumber.toInt(),
              headers = pendingWrite.headers,
              orderedValues = pendingWrite.values,
              accessToken = accessToken,
              attributionOverride = effectiveUser,
            )
          "DELETE" ->
            deleteRecordFromSheet(
              user = admin,
              sheet = sheet,
              worksheet = worksheet,
              worksheetTitle = pendingWrite.worksheetTitle,
              rowNumber = pendingWrite.rowNumber.toInt(),
              recordSummary = pendingWrite.values.firstOrNull().orEmpty(),
              accessToken = accessToken,
              attributionOverride = effectiveUser,
            )
          else -> Result.failure(IllegalArgumentException("Unsupported action: ${pendingWrite.actionType}"))
        }

      if (actionResult.isSuccess) {
        writeRef
          .update(
            mapOf(
              "status" to "APPLIED",
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
        if (pendingWrite.actionType == "ADD") {
          runCatching {
            db.collection("workspaces")
              .document(workspaceId)
              .collection("employees")
              .document(pendingWrite.requestedByUid)
              .update(
                mapOf(
                  "totalRecordsAdded" to FieldValue.increment(1L),
                  "lastActiveAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              )
              .await()
          }
        }
        Result.success(Unit)
      } else {
        val errMsg = actionResult.exceptionOrNull()?.message ?: "Failed to apply change"
        writeRef
          .update(
            mapOf(
              "status" to "FAILED",
              "errorMessage" to errMsg.take(500),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
        Result.failure(actionResult.exceptionOrNull() ?: Exception(errMsg))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun insertRecordToSheet(
    user: UserProfile,
    sheet: SheetConnection,
    worksheet: WorksheetMetadata,
    orderedValues: List<String>,
    accessToken: String,
    attributionOverride: UserProfile? = null,
  ): Result<Int> {
    val workspaceId = user.workspaceId
    return try {
      val uid = requireUserId()
      val appendRes =
        sheetsClient.appendRow(
          accessToken = accessToken,
          spreadsheetId = sheet.spreadsheetId,
          worksheetTitle = worksheet.worksheetTitle,
          values = orderedValues,
        )
      if (appendRes.isFailure) {
        return Result.failure(
          appendRes.exceptionOrNull() ?: Exception("Failed to insert row into Google Sheets.")
        )
      }
      val rowNumber = appendRes.getOrThrow()
      val nowMillis = System.currentTimeMillis()
      val entity =
        buildCachedEntity(
          workspaceId = workspaceId,
          sheetConnectionId = sheet.id,
          spreadsheetId = sheet.spreadsheetId,
          spreadsheetName = sheet.customName,
          worksheetTitle = worksheet.worksheetTitle,
          rowNumber = rowNumber,
          headers = worksheet.headers,
          cells = orderedValues,
          syncedAtMillis = nowMillis,
          isFromCache = false,
        )
      rowDao.upsertAll(listOf(entity))

      if (user.resolvedRole() == UserRole.ADMIN) {
        runCatching {
          val updatedEntities =
            rowDao.observeRowsForWorkspace(workspaceId).let {
              // Publish updated snapshot if small; otherwise syncSheet stays authoritative
              null
            }
          val existingTabRows = findRowsForWorksheet(workspaceId, sheet.id, worksheet.worksheetTitle)
          publishWorksheetRowPages(
            workspaceId = workspaceId,
            sheet = sheet,
            tabTitle = worksheet.worksheetTitle,
            headers = worksheet.headers,
            entities = existingTabRows,
            syncedAtMillis = nowMillis,
          )
        }
      }

      val summaryParts =
        listOfNotNull(
          entity.customerName.takeIf { it.isNotBlank() },
          entity.mobileNumber.takeIf { it.isNotBlank() },
          entity.imei1.takeIf { it.isNotBlank() },
          entity.productModel.takeIf { it.isNotBlank() },
        )
      val summary =
        if (summaryParts.isNotEmpty()) summaryParts.joinToString(" • ").take(500)
        else orderedValues.filter { it.isNotBlank() }.take(3).joinToString(" • ").take(500)
      val imeis = listOf(entity.imei1, entity.imei2).filter { it.isNotBlank() }.take(10)

      val actor = attributionOverride ?: user
      val batch = db.batch()
      val auditRef =
        db.collection("workspaces").document(workspaceId).collection("record_audits").document()
      batch.set(
        auditRef,
        mapOf(
          "workspaceId" to workspaceId,
          "sheetConnectionId" to sheet.id,
          "spreadsheetId" to sheet.spreadsheetId,
          "spreadsheetName" to sheet.customName.take(200),
          "worksheetTitle" to worksheet.worksheetTitle.take(150),
          "rowNumber" to rowNumber.toLong(),
          "actionType" to "ADDED",
          "addedByUid" to uid,
          "addedByName" to actor.displayName.take(120),
          "addedByEmail" to actor.email.take(200),
          "recordSummary" to summary,
          "imeiValues" to imeis,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val wsRef =
        db.collection("workspaces")
          .document(workspaceId)
          .collection("worksheets")
          .document(worksheet.id)
      batch.update(
        wsRef,
        mapOf(
          "recordCount" to FieldValue.increment(1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val sheetRef =
        db.collection("workspaces").document(workspaceId).collection("sheets").document(sheet.id)
      batch.update(
        sheetRef,
        mapOf(
          "totalRecords" to FieldValue.increment(1L),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (user.resolvedRole() == UserRole.EMPLOYEE) {
        val empRef =
          db.collection("workspaces").document(workspaceId).collection("employees").document(uid)
        batch.update(
          empRef,
          mapOf(
            "totalRecordsAdded" to FieldValue.increment(1L),
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
      workspaceRepository.logActivity(
        user = user,
        activityType = "RECORD_ADDED",
        description =
          "Added row #$rowNumber to ${sheet.customName} / ${worksheet.worksheetTitle}" +
            if (attributionOverride != null) " (for ${attributionOverride.displayName})" else "",
        sheetId = sheet.id,
        sheetName = sheet.customName,
        worksheetTitle = worksheet.worksheetTitle,
        rowReference = "Row $rowNumber",
      )
      Result.success(rowNumber)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/record_audits")
      Result.failure(e)
    }
  }

  private suspend fun findRowsForWorksheet(
    workspaceId: String,
    sheetId: String,
    worksheetTitle: String,
  ): List<CachedSheetRowEntity> {
    return rowDao.getWorksheetRows(workspaceId, sheetId, worksheetTitle)
  }

  suspend fun updateRecordInSheet(
    user: UserProfile,
    sheet: SheetConnection,
    worksheetTitle: String,
    rowNumber: Int,
    headers: List<String>,
    orderedValues: List<String>,
    accessToken: String,
    attributionOverride: UserProfile? = null,
  ): Result<Unit> {
    val workspaceId = user.workspaceId
    return try {
      val uid = requireUserId()
      val updateRes =
        sheetsClient.updateRow(
          accessToken = accessToken,
          spreadsheetId = sheet.spreadsheetId,
          worksheetTitle = worksheetTitle,
          rowNumber = rowNumber,
          values = orderedValues,
        )
      if (updateRes.isFailure) {
        return Result.failure(
          updateRes.exceptionOrNull() ?: Exception("Failed to update Google Sheet row.")
        )
      }

      val nowMillis = System.currentTimeMillis()
      val entity =
        buildCachedEntity(
          workspaceId = workspaceId,
          sheetConnectionId = sheet.id,
          spreadsheetId = sheet.spreadsheetId,
          spreadsheetName = sheet.customName,
          worksheetTitle = worksheetTitle,
          rowNumber = rowNumber,
          headers = headers,
          cells = orderedValues,
          syncedAtMillis = nowMillis,
          isFromCache = false,
        )
      rowDao.upsertAll(listOf(entity))

      if (user.resolvedRole() == UserRole.ADMIN) {
        runCatching {
          val existingTabRows = findRowsForWorksheet(workspaceId, sheet.id, worksheetTitle)
          publishWorksheetRowPages(
            workspaceId = workspaceId,
            sheet = sheet,
            tabTitle = worksheetTitle,
            headers = headers,
            entities = existingTabRows,
            syncedAtMillis = nowMillis,
          )
        }
      }

      val actor = attributionOverride ?: user
      val imeis = listOf(entity.imei1, entity.imei2).filter { it.isNotBlank() }.take(10)
      db.collection("workspaces")
        .document(workspaceId)
        .collection("record_audits")
        .document()
        .set(
          mapOf(
            "workspaceId" to workspaceId,
            "sheetConnectionId" to sheet.id,
            "spreadsheetId" to sheet.spreadsheetId,
            "spreadsheetName" to sheet.customName.take(200),
            "worksheetTitle" to worksheetTitle.take(150),
            "rowNumber" to rowNumber.toLong(),
            "actionType" to "EDITED",
            "addedByUid" to uid,
            "addedByName" to actor.displayName.take(120),
            "addedByEmail" to actor.email.take(200),
            "recordSummary" to "Updated Row #$rowNumber (${entity.customerName})".take(500),
            "imeiValues" to imeis,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      workspaceRepository.logActivity(
        user = user,
        activityType = "RECORD_EDITED",
        description = "Edited row #$rowNumber in ${sheet.customName} / $worksheetTitle",
        sheetId = sheet.id,
        sheetName = sheet.customName,
        worksheetTitle = worksheetTitle,
        rowReference = "Row $rowNumber",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/record_audits")
      Result.failure(e)
    }
  }

  suspend fun createNewSpreadsheet(
    admin: UserProfile,
    title: String,
    initialWorksheetTitle: String,
    initialHeaders: List<String>,
    accessToken: String,
  ): Result<SheetConnection> {
    return try {
      requireUserId()
      val createRes =
        sheetsClient.createSpreadsheet(
          accessToken = accessToken,
          title = title,
          initialWorksheetTitle = initialWorksheetTitle,
          initialHeaders = initialHeaders,
        )
      if (createRes.isFailure) {
        return Result.failure(
          createRes.exceptionOrNull() ?: Exception("Failed to create Google Spreadsheet.")
        )
      }
      val preview = createRes.getOrThrow()
      val tabName = preview.worksheets.firstOrNull()?.title ?: initialWorksheetTitle.ifBlank { "Records" }
      connectSpreadsheet(
        admin = admin,
        preview = preview,
        customName = preview.title,
        selectedWorksheetTitles = listOf(tabName),
        accessToken = accessToken,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteRecordFromSheet(
    user: UserProfile,
    sheet: SheetConnection,
    worksheet: WorksheetMetadata?,
    worksheetTitle: String,
    rowNumber: Int,
    recordSummary: String,
    accessToken: String,
    attributionOverride: UserProfile? = null,
  ): Result<Unit> {
    val workspaceId = user.workspaceId
    return try {
      val uid = requireUserId()
      if (rowNumber < 2) {
        return Result.failure(IllegalArgumentException("Header row #1 cannot be deleted."))
      }

      var gid = worksheet?.sheetGid
      if (gid == null || gid == 0L) {
        val metaRes = sheetsClient.getSpreadsheetMetadata(accessToken, sheet.spreadsheetId)
        if (metaRes.isSuccess) {
          gid =
            metaRes
              .getOrThrow()
              .worksheets
              .firstOrNull { it.title.equals(worksheetTitle, ignoreCase = true) }
              ?.sheetId ?: 0L
        }
      }

      val delRes =
        sheetsClient.deleteRow(
          accessToken = accessToken,
          spreadsheetId = sheet.spreadsheetId,
          sheetGid = gid ?: 0L,
          rowNumber = rowNumber,
        )
      if (delRes.isFailure) {
        return Result.failure(
          delRes.exceptionOrNull() ?: Exception("Failed to delete row from Google Sheets.")
        )
      }

      val actor = attributionOverride ?: user
      db.collection("workspaces")
        .document(workspaceId)
        .collection("record_audits")
        .document()
        .set(
          mapOf(
            "workspaceId" to workspaceId,
            "sheetConnectionId" to sheet.id,
            "spreadsheetId" to sheet.spreadsheetId,
            "spreadsheetName" to sheet.customName.take(200),
            "worksheetTitle" to worksheetTitle.take(150),
            "rowNumber" to rowNumber.toLong(),
            "actionType" to "DELETED",
            "addedByUid" to uid,
            "addedByName" to actor.displayName.take(120),
            "addedByEmail" to actor.email.take(200),
            "recordSummary" to "Deleted Row #$rowNumber ($recordSummary)".take(500),
            "imeiValues" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      workspaceRepository.logActivity(
        user = user,
        activityType = "RECORD_DELETED",
        description = "Deleted row #$rowNumber from ${sheet.customName} / $worksheetTitle",
        sheetId = sheet.id,
        sheetName = sheet.customName,
        worksheetTitle = worksheetTitle,
        rowReference = "Row $rowNumber",
      )

      syncSheet(user = user, sheet = sheet, accessToken = accessToken)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/record_audits")
      Result.failure(e)
    }
  }

  suspend fun findDuplicateImei(workspaceId: String, imei: String): List<CachedSheetRowEntity> {
    val clean = imei.trim()
    if (clean.length < 8) return emptyList()
    return rowDao.findDuplicateImei(workspaceId, clean)
  }

  private suspend fun recordSyncFailure(
    user: UserProfile,
    sheet: SheetConnection,
    errorMessage: String,
    durationMs: Long,
  ) {
    val workspaceId = user.workspaceId
    try {
      rowDao.markSheetRowsCached(sheet.id)
      if (user.resolvedRole() == UserRole.ADMIN) {
        db.collection("workspaces")
          .document(workspaceId)
          .collection("sheets")
          .document(sheet.id)
          .update(
            mapOf(
              "connectionHealth" to ConnectionHealth.ERROR.name,
              "lastSyncError" to errorMessage.take(500),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      db.collection("workspaces")
        .document(workspaceId)
        .collection("sync_logs")
        .document()
        .set(
          mapOf(
            "workspaceId" to workspaceId,
            "sheetConnectionId" to sheet.id,
            "sheetName" to sheet.customName.take(200),
            "triggeredByUid" to user.userId,
            "triggeredByName" to user.displayName.take(120),
            "status" to "FAILED",
            "recordsSynced" to 0L,
            "errorMessage" to errorMessage.take(500),
            "durationMs" to durationMs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      workspaceRepository.logActivity(
        user = user,
        activityType = "SYNC_ERROR",
        description = "Sync failed for '${sheet.customName}': ${errorMessage.take(200)}",
        sheetId = sheet.id,
        sheetName = sheet.customName,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/sync_logs")
    }
  }

  companion object {
    const val ROWS_PER_CLOUD_PAGE = 250

    fun buildWorksheetDocId(sheetConnectionId: String, worksheetTitle: String): String {
      val sanitized = worksheetTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
      val hash = worksheetTitle.hashCode().toUInt().toString(16)
      return "${sheetConnectionId}_${sanitized}_$hash".take(120)
    }

    fun buildRowPageDocId(
      sheetConnectionId: String,
      worksheetTitle: String,
      pageIndex: Int,
    ): String {
      val sanitized = worksheetTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36)
      val hash = worksheetTitle.hashCode().toUInt().toString(16)
      return "pg_${sheetConnectionId.take(40)}_${sanitized}_${hash}_$pageIndex".take(120)
    }

    fun buildCachedEntity(
      workspaceId: String,
      sheetConnectionId: String,
      spreadsheetId: String,
      spreadsheetName: String,
      worksheetTitle: String,
      rowNumber: Int,
      headers: List<String>,
      cells: List<String>,
      syncedAtMillis: Long,
      isFromCache: Boolean,
    ): CachedSheetRowEntity {
      fun findCell(vararg keywords: String): String {
        val idx =
          headers.indexOfFirst { h ->
            val lower = h.lowercase()
            keywords.any { lower.contains(it) }
          }
        return if (idx >= 0) cells.getOrNull(idx).orEmpty().trim() else ""
      }

      val imei1Idx =
        headers.indexOfFirst { h ->
          val l = h.lowercase()
          l.contains("imei 1") || l.contains("imei1") || (l.contains("imei") && !l.contains("2"))
        }
      val imei2Idx =
        headers.indexOfFirst { h ->
          val l = h.lowercase()
          l.contains("imei 2") || l.contains("imei2")
        }

      val imei1 = if (imei1Idx >= 0) cells.getOrNull(imei1Idx).orEmpty().trim() else ""
      val imei2 = if (imei2Idx >= 0) cells.getOrNull(imei2Idx).orEmpty().trim() else ""
      val customer = findCell("customer", "client", "buyer", "name")
      val mobile = findCell("mobile", "phone", "contact", "whatsapp")
      val aadhaar = findCell("aadhaar", "aadhar", "uidai")
      val retailer = findCell("retailer", "dealer", "shop", "store", "branch")
      val invoice = findCell("invoice", "bill", "receipt")
      val model = findCell("model", "product", "device", "handset", "item")
      val finance = findCell("finance", "emi", "loan", "financer", "bank", "downpayment")
      val date = findCell("date", "created", "purchase")

      val searchableText =
        buildString {
          append(spreadsheetName)
          append(' ')
          append(worksheetTitle)
          for (cell in cells) {
            if (cell.isNotBlank()) {
              append(' ')
              append(cell.trim())
            }
          }
        }.lowercase()

      val id =
        "${sheetConnectionId}_${worksheetTitle.hashCode().toUInt().toString(16)}_r$rowNumber"

      return CachedSheetRowEntity(
        id = id,
        workspaceId = workspaceId,
        sheetConnectionId = sheetConnectionId,
        spreadsheetId = spreadsheetId,
        spreadsheetName = spreadsheetName,
        worksheetTitle = worksheetTitle,
        rowNumber = rowNumber,
        headersJson = JSONArray(headers).toString(),
        cellsJson = JSONArray(cells).toString(),
        searchableText = searchableText,
        customerName = customer,
        mobileNumber = mobile,
        imei1 = imei1,
        imei2 = imei2,
        aadhaarNumber = aadhaar,
        retailerName = retailer,
        invoiceNumber = invoice,
        productModel = model,
        financeInfo = finance,
        recordDate = date,
        syncedAtMillis = syncedAtMillis,
        isFromCache = isFromCache,
      )
    }
  }
}
