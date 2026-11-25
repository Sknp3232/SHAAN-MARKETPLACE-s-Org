package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.model.AccountStatus
import com.example.model.ActivityLog
import com.example.model.EmployeeRecord
import com.example.model.Invitation
import com.example.model.InvitationStatus
import com.example.model.PendingSheetWrite
import com.example.model.RecordAudit
import com.example.model.SavedSearch
import com.example.model.SheetConnection
import com.example.model.SheetPermissionLevel
import com.example.model.SheetRowPage
import com.example.model.SyncLog
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.WorksheetMetadata
import com.example.model.WorkspaceSettings
import com.example.util.InvitationValidator
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WorkspaceRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  fun observeSheets(workspaceId: String, isAdmin: Boolean, uid: String): Flow<List<SheetConnection>> =
    flow {
      if (uid.isBlank() || workspaceId.isBlank()) return@flow
      val path = "workspaces/$workspaceId/sheets"
      val query =
        if (isAdmin) {
          db.collection("workspaces").document(workspaceId).collection("sheets")
        } else {
          db.collection("workspaces")
            .document(workspaceId)
            .collection("sheets")
            .whereArrayContains("assignedEmployeeUids", uid)
        }
      emitAll(
        query
          .snapshots()
          .map { snap ->
            snap
              .toObjects(
                SheetConnection::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
              .let { list -> if (isAdmin) list else list.filter { it.active } }
              .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
          }
          .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
      )
    }

  fun observeWorksheets(
    workspaceId: String,
    isAdmin: Boolean,
    uid: String,
  ): Flow<List<WorksheetMetadata>> = flow {
    if (uid.isBlank() || workspaceId.isBlank()) return@flow
    val path = "workspaces/$workspaceId/worksheets"
    val query =
      if (isAdmin) {
        db.collection("workspaces").document(workspaceId).collection("worksheets")
      } else {
        db.collection("workspaces")
          .document(workspaceId)
          .collection("worksheets")
          .whereArrayContains("assignedEmployeeUids", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snap ->
          snap.toObjects(
            WorksheetMetadata::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
    )
  }

  fun observeSheetRowPages(
    workspaceId: String,
    sheetId: String,
    uid: String,
  ): Flow<List<SheetRowPage>> = flow {
    if (uid.isBlank() || workspaceId.isBlank() || sheetId.isBlank()) return@flow
    val path = "workspaces/$workspaceId/sheets/$sheetId/row_pages"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("sheets")
        .document(sheetId)
        .collection("row_pages")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              SheetRowPage::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedWith(compareBy<SheetRowPage> { it.worksheetTitle }.thenBy { it.pageIndex })
        }
        .catch { err ->
          val firestoreErr =
            generateSequence(err) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            emit(emptyList())
          } else {
            handleFlowError(err, OperationType.LIST, path, uid)
          }
        }
    )
  }

  fun observePendingWritesForSheet(
    workspaceId: String,
    sheetId: String,
    uid: String,
  ): Flow<List<PendingSheetWrite>> = flow {
    if (uid.isBlank() || workspaceId.isBlank() || sheetId.isBlank()) return@flow
    val path = "workspaces/$workspaceId/sheets/$sheetId/pending_writes"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("sheets")
        .document(sheetId)
        .collection("pending_writes")
        .whereEqualTo("status", "PENDING")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              PendingSheetWrite::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
    )
  }

  fun observeEmployees(workspaceId: String): Flow<List<EmployeeRecord>> = flow {
    val uid = requireUserId()
    val path = "workspaces/$workspaceId/employees"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("employees")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              EmployeeRecord::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.joinedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
    )
  }

  fun observeMyEmployeeRecord(workspaceId: String, employeeUid: String): Flow<EmployeeRecord?> =
    flow {
      val uid = requireUserId()
      val path = "workspaces/$workspaceId/employees/$employeeUid"
      emitAll(
        db.collection("workspaces")
          .document(workspaceId)
          .collection("employees")
          .document(employeeUid)
          .snapshots()
          .map { snap ->
            snap.toObject(
              EmployeeRecord::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          }
          .catch { err -> handleFlowError(err, OperationType.GET, path, uid) }
      )
    }

  fun observeInvitations(adminUid: String): Flow<List<Invitation>> = flow {
    val uid = requireUserId()
    val path = "invitations"
    emitAll(
      db.collection("invitations")
        .whereEqualTo("adminUid", adminUid)
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              Invitation::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
    )
  }

  fun observeActivityLogs(
    workspaceId: String,
    isAdmin: Boolean,
    uid: String,
  ): Flow<List<ActivityLog>> = flow {
    if (uid.isBlank() || workspaceId.isBlank()) return@flow
    val path = "workspaces/$workspaceId/activity_logs"
    val query =
      if (isAdmin) {
        db.collection("workspaces").document(workspaceId).collection("activity_logs")
      } else {
        db.collection("workspaces")
          .document(workspaceId)
          .collection("activity_logs")
          .whereEqualTo("userId", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              ActivityLog::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            .take(250)
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
    )
  }

  fun observeRecordAudits(
    workspaceId: String,
    isAdmin: Boolean,
    uid: String,
  ): Flow<List<RecordAudit>> = flow {
    if (uid.isBlank() || workspaceId.isBlank()) return@flow
    val path = "workspaces/$workspaceId/record_audits"
    val query =
      if (isAdmin) {
        db.collection("workspaces").document(workspaceId).collection("record_audits")
      } else {
        db.collection("workspaces")
          .document(workspaceId)
          .collection("record_audits")
          .whereEqualTo("addedByUid", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              RecordAudit::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
    )
  }

  fun observeSyncLogs(workspaceId: String, isAdmin: Boolean, uid: String): Flow<List<SyncLog>> =
    flow {
      if (uid.isBlank() || workspaceId.isBlank()) return@flow
      val path = "workspaces/$workspaceId/sync_logs"
      val query =
        if (isAdmin) {
          db.collection("workspaces").document(workspaceId).collection("sync_logs")
        } else {
          db.collection("workspaces")
            .document(workspaceId)
            .collection("sync_logs")
            .whereEqualTo("triggeredByUid", uid)
        }
      emitAll(
        query
          .snapshots()
          .map { snap ->
            snap
              .toObjects(
                SyncLog::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
              .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
              .take(100)
          }
          .catch { err -> handleFlowError(err, OperationType.LIST, path, uid) }
      )
    }

  fun observeSettings(workspaceId: String): Flow<WorkspaceSettings> = flow {
    val uid = requireUserId()
    val path = "workspaces/$workspaceId/settings/config"
    emitAll(
      db.collection("workspaces")
        .document(workspaceId)
        .collection("settings")
        .document("config")
        .snapshots()
        .map { snap ->
          snap.toObject(
            WorkspaceSettings::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: WorkspaceSettings(workspaceId = workspaceId)
        }
        .catch { err -> handleFlowError(err, OperationType.GET, path, uid) }
    )
  }

  fun observeSavedSearches(uid: String): Flow<List<SavedSearch>> = flow {
    val currentUid = requireUserId()
    val path = "users/$uid/saved_searches"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("saved_searches")
        .snapshots()
        .map { snap ->
          snap
            .toObjects(
              SavedSearch::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
        .catch { err -> handleFlowError(err, OperationType.LIST, path, currentUid) }
    )
  }

  suspend fun createInvitation(
    admin: UserProfile,
    targetEmail: String,
    defaultPermission: SheetPermissionLevel,
    assignedSheetIds: List<String>,
    sheetPermissions: Map<String, String> = emptyMap(),
    maxUses: Long = 1L,
    expiryDays: Int? = 7,
    allSheets: List<SheetConnection> = emptyList(),
    allWorksheets: List<WorksheetMetadata> = emptyList(),
  ): Result<Invitation> {
    val emailError = InvitationValidator.validateEmailOrError(targetEmail)
    if (emailError != null) {
      return Result.failure(IllegalArgumentException(emailError))
    }
    val normalizedEmail = InvitationValidator.normalizeEmail(targetEmail).take(200)
    val cleanSheetIds = assignedSheetIds.distinct().take(50)
    val cleanSheetPermissions = sheetPermissions.filterKeys { it in cleanSheetIds }

    val code = "SM-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
    val path = "invitations/$code"
    return try {
      val uid = requireUserId()
      val expiresAt =
        expiryDays?.takeIf { it > 0 }?.let { days ->
          Timestamp(Date(System.currentTimeMillis() + days * 86_400_000L))
        }
      val payload =
        mapOf(
          "inviteCode" to code,
          "adminUid" to uid,
          "adminName" to admin.displayName.take(120),
          "adminEmail" to admin.email.take(200),
          "workspaceId" to uid,
          "targetEmail" to normalizedEmail,
          "role" to UserRole.EMPLOYEE.name,
          "defaultPermission" to defaultPermission.name,
          "assignedSheetIds" to cleanSheetIds,
          "sheetPermissions" to cleanSheetPermissions,
          "maxUses" to maxUses.coerceIn(1L, 1000L),
          "usedCount" to 0L,
          "status" to InvitationStatus.ACTIVE.name,
          "expiresAt" to expiresAt,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }

      val emailIndexPayload =
        mapOf(
          "email" to normalizedEmail,
          "inviteCode" to code,
          "adminUid" to uid,
          "workspaceId" to uid,
          "role" to UserRole.EMPLOYEE.name,
          "status" to InvitationStatus.ACTIVE.name,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )

      val batch = db.batch()
      batch.set(db.collection("invitations").document(code), payload)
      batch.set(db.collection("email_invites").document(normalizedEmail), emailIndexPayload)
      batch.commit().await()

      // If this employee is already registered in this workspace, apply updated sheets/permissions immediately
      val existingEmployees =
        runCatching {
          db.collection("workspaces")
            .document(admin.workspaceId)
            .collection("employees")
            .get()
            .await()
            .toObjects(EmployeeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrDefault(emptyList())
      val matchingExisting =
        existingEmployees.firstOrNull {
          InvitationValidator.normalizeEmail(it.email) == normalizedEmail
        }
      if (matchingExisting != null) {
        updateEmployeeAccess(
          admin = admin,
          employee = matchingExisting,
          newStatus = AccountStatus.ACTIVE,
          newDefaultPermission = defaultPermission,
          newAssignedSheetIds = cleanSheetIds,
          newSheetPermissions = cleanSheetPermissions,
          allSheets = allSheets,
          allWorksheets = allWorksheets,
        )
      }

      logActivity(
        user = admin,
        activityType = "INVITE_CREATED",
        description =
          "Invited $normalizedEmail as EMPLOYEE ($code, ${cleanSheetIds.size} sheets, ${defaultPermission.label})",
      )
      val snap = db.collection("invitations").document(code).get().await()
      val created =
        snap.toObject(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: Invitation(
            id = code,
            inviteCode = code,
            adminUid = uid,
            adminName = admin.displayName,
            adminEmail = admin.email,
            workspaceId = uid,
            targetEmail = normalizedEmail,
            role = UserRole.EMPLOYEE.name,
            defaultPermission = defaultPermission.name,
            assignedSheetIds = cleanSheetIds,
            sheetPermissions = cleanSheetPermissions,
            maxUses = maxUses,
          )
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun revokeInvitation(admin: UserProfile, inviteCode: String): Result<Unit> {
    val path = "invitations/$inviteCode"
    return try {
      requireUserId()
      val invRef = db.collection("invitations").document(inviteCode)
      val invSnap = invRef.get().await()
      val targetEmail = InvitationValidator.normalizeEmail(invSnap.getString("targetEmail"))

      val batch = db.batch()
      batch.update(
        invRef,
        mapOf(
          "status" to InvitationStatus.REVOKED.name,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (targetEmail.isNotBlank()) {
        val emailInviteRef = db.collection("email_invites").document(targetEmail)
        val emailSnap = runCatching { emailInviteRef.get().await() }.getOrNull()
        if (emailSnap != null && emailSnap.exists() && emailSnap.getString("inviteCode") == inviteCode) {
          batch.update(
            emailInviteRef,
            mapOf(
              "status" to InvitationStatus.REVOKED.name,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      batch.commit().await()

      logActivity(
        user = admin,
        activityType = "INVITE_REVOKED",
        description = "Revoked employee invitation $inviteCode",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateEmployeeAccess(
    admin: UserProfile,
    employee: EmployeeRecord,
    newStatus: AccountStatus,
    newDefaultPermission: SheetPermissionLevel,
    newAssignedSheetIds: List<String>,
    newSheetPermissions: Map<String, String>,
    allSheets: List<SheetConnection>,
    allWorksheets: List<WorksheetMetadata>,
  ): Result<Unit> {
    val workspaceId = admin.workspaceId
    val path = "workspaces/$workspaceId/employees/${employee.employeeUid}"
    return try {
      requireUserId()
      val batch = db.batch()
      val empRef =
        db.collection("workspaces")
          .document(workspaceId)
          .collection("employees")
          .document(employee.employeeUid)

      batch.update(
        empRef,
        mapOf(
          "status" to newStatus.name,
          "defaultPermission" to newDefaultPermission.name,
          "assignedSheetIds" to newAssignedSheetIds.distinct().take(100),
          "sheetPermissions" to newSheetPermissions,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      // Keep assignedEmployeeUids synchronized on each SheetConnection & WorksheetMetadata
      val shouldHaveAccess = newStatus == AccountStatus.ACTIVE
      for (sheet in allSheets) {
        val currentlyAssigned = employee.employeeUid in sheet.assignedEmployeeUids
        val shouldBeAssigned = shouldHaveAccess && sheet.id in newAssignedSheetIds
        if (currentlyAssigned != shouldBeAssigned) {
          val updatedUids =
            if (shouldBeAssigned) {
              (sheet.assignedEmployeeUids + employee.employeeUid).distinct()
            } else {
              sheet.assignedEmployeeUids.filterNot { it == employee.employeeUid }
            }
          val sheetRef =
            db.collection("workspaces")
              .document(workspaceId)
              .collection("sheets")
              .document(sheet.id)
          batch.update(
            sheetRef,
            mapOf(
              "assignedEmployeeUids" to updatedUids,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          for (ws in allWorksheets.filter { it.sheetConnectionId == sheet.id }) {
            val wsRef =
              db.collection("workspaces")
                .document(workspaceId)
                .collection("worksheets")
                .document(ws.id)
            batch.update(
              wsRef,
              mapOf(
                "assignedEmployeeUids" to updatedUids,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
      }

      batch.commit().await()
      logActivity(
        user = admin,
        activityType = "PERMISSION_UPDATED",
        description =
          "Updated ${employee.name}: status=${newStatus.name}, permission=${newDefaultPermission.label}, sheets=${newAssignedSheetIds.size}",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateEmployeeProfileDetails(
    admin: UserProfile,
    employeeUid: String,
    name: String,
    email: String,
  ): Result<Unit> {
    val path = "workspaces/${admin.workspaceId}/employees/$employeeUid"
    return try {
      requireUserId()
      db.document(path)
        .update(
          mapOf(
            "name" to name.trim().take(120),
            "email" to InvitationValidator.normalizeEmail(email).take(200),
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

  suspend fun removeEmployee(
    admin: UserProfile,
    employee: EmployeeRecord,
    allSheets: List<SheetConnection>,
    allWorksheets: List<WorksheetMetadata>,
  ): Result<Unit> {
    val workspaceId = admin.workspaceId
    val path = "workspaces/$workspaceId/employees/${employee.employeeUid}"
    return try {
      requireUserId()
      val batch = db.batch()
      batch.delete(db.document(path))
      for (sheet in allSheets.filter { employee.employeeUid in it.assignedEmployeeUids }) {
        val updatedUids = sheet.assignedEmployeeUids.filterNot { it == employee.employeeUid }
        batch.update(
          db.collection("workspaces").document(workspaceId).collection("sheets").document(sheet.id),
          mapOf(
            "assignedEmployeeUids" to updatedUids,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        for (ws in allWorksheets.filter { it.sheetConnectionId == sheet.id }) {
          batch.update(
            db.collection("workspaces")
              .document(workspaceId)
              .collection("worksheets")
              .document(ws.id),
            mapOf(
              "assignedEmployeeUids" to updatedUids,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      // Also revoke matching email_invite so removed employee isn't locked in forever unintentionally
      val normEmail = InvitationValidator.normalizeEmail(employee.email)
      if (normEmail.isNotBlank()) {
        val emailInviteRef = db.collection("email_invites").document(normEmail)
        val snap = runCatching { emailInviteRef.get().await() }.getOrNull()
        if (snap != null && snap.exists() && snap.getString("adminUid") == workspaceId) {
          batch.update(
            emailInviteRef,
            mapOf(
              "status" to InvitationStatus.REVOKED.name,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      batch.commit().await()
      logActivity(
        user = admin,
        activityType = "EMPLOYEE_REMOVED",
        description = "Removed employee access for ${employee.name} (${employee.email})",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun saveWorkspaceSettings(
    admin: UserProfile,
    settings: WorkspaceSettings,
  ): Result<Unit> {
    val path = "workspaces/${admin.workspaceId}/settings/config"
    return try {
      val uid = requireUserId()
      db.document(path)
        .set(
          mapOf(
            "workspaceId" to admin.workspaceId,
            "autoSyncIntervalMinutes" to settings.autoSyncIntervalMinutes.coerceIn(5L, 1440L),
            "maskAadhaarByDefault" to settings.maskAadhaarByDefault,
            "allowEmployeeExport" to settings.allowEmployeeExport,
            "duplicateImeiCheckEnabled" to settings.duplicateImeiCheckEnabled,
            "updatedByUid" to uid,
            "createdAt" to (settings.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      logActivity(
        user = admin,
        activityType = "SETTINGS_UPDATED",
        description =
          "Updated workspace settings (Sync=${settings.autoSyncIntervalMinutes}m, AadhaarMask=${settings.maskAadhaarByDefault})",
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun saveOrRecordSearch(
    user: UserProfile,
    query: String,
    filtersSummary: String,
    resultCount: Long,
    saved: Boolean,
  ): Result<Unit> {
    val cleanQuery = query.trim().take(200)
    if (cleanQuery.isBlank()) return Result.success(Unit)
    val uid = user.userId
    val docId =
      "srch_${cleanQuery.lowercase().replace(Regex("[^a-z0-9]"), "_").take(40)}_${if (saved) "saved" else "recent"}"
    val path = "users/$uid/saved_searches/$docId"
    return try {
      requireUserId()
      db.document(path)
        .set(
          mapOf(
            "userId" to uid,
            "query" to cleanQuery,
            "filtersSummary" to filtersSummary.take(300),
            "saved" to saved,
            "resultCount" to resultCount.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      if (user.resolvedRole() == UserRole.EMPLOYEE) {
        val empRef =
          db.collection("workspaces")
            .document(user.workspaceId)
            .collection("employees")
            .document(uid)
        runCatching {
          empRef
            .update(
              mapOf(
                "totalSearches" to FieldValue.increment(1L),
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
      }

      logActivity(
        user = user,
        activityType = "SEARCH_EXECUTED",
        description = "Searched '$cleanQuery' ($resultCount matches)",
        searchQuery = cleanQuery,
      )
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteSavedSearch(uid: String, searchId: String): Result<Unit> {
    val path = "users/$uid/saved_searches/$searchId"
    return try {
      requireUserId()
      db.document(path).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun logActivity(
    user: UserProfile,
    activityType: String,
    description: String,
    sheetId: String? = null,
    sheetName: String? = null,
    worksheetTitle: String? = null,
    rowReference: String? = null,
    searchQuery: String? = null,
  ) {
    val path = "workspaces/${user.workspaceId}/activity_logs"
    try {
      val uid = auth.currentUser?.uid ?: return
      if (uid != user.userId) return
      val payload =
        mapOf(
          "workspaceId" to user.workspaceId,
          "userId" to uid,
          "userName" to user.displayName.take(120),
          "userEmail" to user.email.take(200),
          "userRole" to user.role,
          "activityType" to activityType.take(64),
          "description" to description.take(500),
          "sheetId" to sheetId?.take(128),
          "sheetName" to sheetName?.take(200),
          "worksheetTitle" to worksheetTitle?.take(150),
          "rowReference" to rowReference?.take(64),
          "searchQuery" to searchQuery?.take(200),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null }
      db.collection("workspaces")
        .document(user.workspaceId)
        .collection("activity_logs")
        .document()
        .set(payload)
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
    }
  }

  private fun handleFlowError(
    error: Throwable,
    op: OperationType,
    path: String,
    uid: String,
  ): Nothing {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val toLog = firestoreError ?: (error as? Exception)
    if (toLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(toLog, op, path)
    }
    throw error
  }
}
