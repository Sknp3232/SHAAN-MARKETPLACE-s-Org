package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.model.AccountStatus
import com.example.model.EmployeeRecord
import com.example.model.Invitation
import com.example.model.InvitationStatus
import com.example.model.SheetConnection
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.util.InvitationValidator
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth -> trySend(firebaseAuth.currentUser) }
    auth.addAuthStateListener(listener)
    awaitClose { auth.removeAuthStateListener(listener) }
  }

  fun currentUser(): FirebaseUser? = auth.currentUser

  fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
  ) {
    if (auth.currentUser != null) {
      onAuthSuccess()
      return
    }
    val clientId =
      try {
        context.getString(R.string.default_web_client_id)
      } catch (_: Exception) {
        onUnauthenticated()
        return
      }
    val googleIdOption =
      GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
    scope.launch {
      try {
        val cred = credentialManager.getCredential(context, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
          auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
          onAuthSuccess()
        } else {
          onUnauthenticated()
        }
      } catch (_: Exception) {
        onUnauthenticated()
      }
    }
  }

  fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {},
  ) {
    val clientId =
      try {
        context.getString(R.string.default_web_client_id)
      } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing.")
        return
      }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    scope.launch {
      try {
        val activityContext = context as? Activity
        if (activityContext == null) {
          onAuthError("Unable to launch sign-in without an Activity context.")
          return@launch
        }
        val cred = credentialManager.getCredential(activityContext, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
          auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
          onAuthSuccess()
        } else {
          onAuthError("Unexpected credential type returned.")
        }
      } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
        onAuthCancelled()
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed", e)
        onAuthError(e.localizedMessage ?: "Sign in failed")
      }
    }
  }

  suspend fun signOut(credentialManager: CredentialManager) {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w(TAG, "Failed to clear credential state: ${e.message}")
      } finally {
        auth.signOut()
      }
    }
  }

  private val profileMutex = kotlinx.coroutines.sync.Mutex()

  /**
   * Resolves or creates the user's profile following Automatic Employee Role Identification:
   * 1. Retrieve the authenticated user's verified email address and normalize it.
   * 2. Check pending invite code OR email_invites / invitations for an active invite matching the email.
   * 3. If an invitation exists:
   *    - If already an ACTIVE employee of that workspace (e.g. re-opening same invite link), reconcile sheets and succeed.
   *    - Otherwise create/transition profile + employee record atomically, increment invite usage, and log acceptance AFTER commit.
   * 4. If no invitation exists: preserve existing profile or register independent ADMIN workspace.
   */
  suspend fun ensureUserProfile(pendingInviteCode: String?): Result<UserProfile> =
    profileMutex.withLock {
      val firebaseUser =
        auth.currentUser
          ?: return@withLock Result.failure(IllegalStateException("User must be signed in with Google."))
      val uid = firebaseUser.uid
      val normalizedEmail =
        InvitationValidator.normalizeEmail(firebaseUser.email).ifBlank { "$uid@smartsheet.local" }
      val displayName =
        firebaseUser.displayName?.trim().orEmpty().ifBlank { normalizedEmail.substringBefore("@").ifBlank { "User" } }
      val photoUrl = firebaseUser.photoUrl?.toString()?.takeIf { it.isNotBlank() }
      val userPath = "users/$uid"
      val cleanCode = pendingInviteCode?.trim()?.uppercase().orEmpty()

      return@withLock try {
        val existingSnap = db.collection("users").document(uid).get().await()
        val existingProfile =
          if (existingSnap.exists()) {
            existingSnap.toObject(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: return@withLock Result.failure(IllegalStateException("Corrupt user profile."))
          } else {
            null
          }

        val existingEmpSnap =
          if (existingProfile?.resolvedRole() == UserRole.EMPLOYEE && existingProfile.workspaceId.isNotBlank()) {
            runCatching {
              db.collection("workspaces")
                .document(existingProfile.workspaceId)
                .collection("employees")
                .document(uid)
                .get()
                .await()
            }.getOrNull()
          } else {
            null
          }
        val existingEmp =
          existingEmpSnap
            ?.takeIf { it.exists() }
            ?.toObject(EmployeeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

        // Fast path: already an ACTIVE employee of a workspace and opening without a conflicting new invite link
        if (existingProfile != null &&
          existingProfile.resolvedRole() == UserRole.EMPLOYEE &&
          existingEmp != null &&
          existingEmp.resolvedStatus() == AccountStatus.ACTIVE
        ) {
          val reopenedSameInvite =
            cleanCode.isNotBlank() && cleanCode == existingProfile.inviteCodeUsed
          if (cleanCode.isBlank() || reopenedSameInvite) {
            if (existingEmp.assignedSheetIds.isNotEmpty()) {
              reconcileEmployeeSheetMemberships(
                workspaceId = existingProfile.workspaceId,
                employeeUid = uid,
                assignedSheetIds = existingEmp.assignedSheetIds,
              )
            }
            return@withLock Result.success(existingProfile)
          }
        }

        // Resolve any active invitation (explicit deep-link/code, O(1) email_invites, or invitations query)
        val matchedInvite =
          resolveActiveInvitationForLogin(
            cleanCode = cleanCode,
            normalizedEmail = normalizedEmail,
          ).getOrElse { err ->
            return@withLock Result.failure(err)
          }

        if (matchedInvite != null) {
          // If already active in this exact inviting workspace with this exact invite, don't re-create or burn a use
          if (existingProfile != null &&
            existingProfile.resolvedRole() == UserRole.EMPLOYEE &&
            existingProfile.workspaceId == matchedInvite.adminUid &&
            existingEmp != null &&
            existingEmp.resolvedStatus() == AccountStatus.ACTIVE
          ) {
            reconcileEmployeeSheetMemberships(
              workspaceId = existingProfile.workspaceId,
              employeeUid = uid,
              assignedSheetIds = existingEmp.assignedSheetIds,
            )
            return@withLock Result.success(existingProfile)
          }

          // If transitioning from another workspace, remove stale old employee doc first
          if (existingProfile != null &&
            existingProfile.resolvedRole() == UserRole.EMPLOYEE &&
            existingProfile.workspaceId.isNotBlank() &&
            existingProfile.workspaceId != matchedInvite.adminUid
          ) {
            runCatching {
              db.collection("workspaces")
                .document(existingProfile.workspaceId)
                .collection("employees")
                .document(uid)
                .delete()
                .await()
            }
          }

          val redeemCode = matchedInvite.inviteCode
          val safeAdminName = matchedInvite.adminName.trim().ifBlank { "Workspace Admin" }.take(120)
          val safeAdminEmail = matchedInvite.adminEmail.trim().ifBlank { "admin@smartsheet.local" }.take(200)
          val safeDisplayName = displayName.trim().ifBlank { "Employee" }.take(120)
          val invitedAtValue: Any = matchedInvite.createdAt ?: Timestamp(0, 0)

          val batch = db.batch()
          val userRef = db.collection("users").document(uid)
          if (existingSnap.exists()) {
            val userUpdate =
              mutableMapOf<String, Any>(
                "email" to normalizedEmail.take(200),
                "displayName" to safeDisplayName,
                "role" to UserRole.EMPLOYEE.name,
                "workspaceId" to matchedInvite.adminUid,
                "inviteCodeUsed" to redeemCode,
                "addedByAdminUid" to matchedInvite.adminUid,
                "addedByAdminName" to safeAdminName,
                "addedByAdminEmail" to safeAdminEmail,
                "invitedAt" to invitedAtValue,
                "joinedAt" to FieldValue.serverTimestamp(),
                "status" to AccountStatus.ACTIVE.name,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            if (photoUrl != null) {
              userUpdate["photoUrl"] = photoUrl.take(500)
            }
            batch.update(userRef, userUpdate)
          } else {
            val userPayload =
              mapOf(
                "userId" to uid,
                "email" to normalizedEmail.take(200),
                "displayName" to safeDisplayName,
                "photoUrl" to photoUrl?.take(500),
                "role" to UserRole.EMPLOYEE.name,
                "workspaceId" to matchedInvite.adminUid,
                "inviteCodeUsed" to redeemCode,
                "addedByAdminUid" to matchedInvite.adminUid,
                "addedByAdminName" to safeAdminName,
                "addedByAdminEmail" to safeAdminEmail,
                "invitedAt" to invitedAtValue,
                "joinedAt" to FieldValue.serverTimestamp(),
                "status" to AccountStatus.ACTIVE.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ).filterValues { it != null }
            batch.set(userRef, userPayload)
          }

          val employeeRef =
            db.collection("workspaces")
              .document(matchedInvite.adminUid)
              .collection("employees")
              .document(uid)
          val targetEmpSnap = runCatching { employeeRef.get().await() }.getOrNull()
          if (targetEmpSnap == null || !targetEmpSnap.exists()) {
            val empCode = "EMP-${uid.take(6).uppercase()}"
            val employeePayload =
              mapOf(
                "employeeUid" to uid,
                "workspaceId" to matchedInvite.adminUid,
                "employeeCode" to empCode,
                "name" to safeDisplayName,
                "email" to normalizedEmail.take(200),
                "photoUrl" to photoUrl?.take(500),
                "addedByAdminUid" to matchedInvite.adminUid,
                "addedByAdminName" to safeAdminName,
                "addedByAdminEmail" to safeAdminEmail,
                "inviteCode" to redeemCode,
                "invitedAt" to invitedAtValue,
                "joinedAt" to FieldValue.serverTimestamp(),
                "status" to AccountStatus.ACTIVE.name,
                "defaultPermission" to matchedInvite.defaultPermission,
                "assignedSheetIds" to matchedInvite.assignedSheetIds,
                "sheetPermissions" to matchedInvite.sheetPermissions,
                "totalSearches" to 0L,
                "totalRecordsAdded" to 0L,
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              ).filterValues { it != null }
            batch.set(employeeRef, employeePayload)
          }

          val inviteRef = db.collection("invitations").document(redeemCode)
          batch.update(
            inviteRef,
            mapOf(
              "usedCount" to FieldValue.increment(1L),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )

          // Mark email_invites EXPIRED once maxUses is consumed so the email isn't locked permanently across workspaces
          if (matchedInvite.usedCount + 1L >= matchedInvite.maxUses &&
            matchedInvite.normalizedTargetEmail() == normalizedEmail
          ) {
            val emailInviteRef = db.collection("email_invites").document(normalizedEmail)
            val emailSnap = runCatching { emailInviteRef.get().await() }.getOrNull()
            if (emailSnap != null &&
              emailSnap.exists() &&
              emailSnap.getString("inviteCode") == redeemCode &&
              emailSnap.getString("status") == InvitationStatus.ACTIVE.name
            ) {
              batch.update(
                emailInviteRef,
                mapOf(
                  "status" to InvitationStatus.EXPIRED.name,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              )
            }
          }

          batch.commit().await()

          // Log acceptance AFTER commit so Firestore security rules see the newly committed employee doc
          runCatching {
            db.collection("workspaces")
              .document(matchedInvite.adminUid)
              .collection("activity_logs")
              .document()
              .set(
                mapOf(
                  "workspaceId" to matchedInvite.adminUid,
                  "userId" to uid,
                  "userName" to safeDisplayName,
                  "userEmail" to normalizedEmail.take(200),
                  "userRole" to UserRole.EMPLOYEE.name,
                  "activityType" to "INVITE_ACCEPTED",
                  "description" to
                    "Automatically identified and joined workspace via invitation $redeemCode from $safeAdminName",
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              )
              .await()
          }

          // Immediately sync assignedEmployeeUids on sheets & worksheets so assigned sheets appear instantly
          reconcileEmployeeSheetMemberships(
            workspaceId = matchedInvite.adminUid,
            employeeUid = uid,
            assignedSheetIds = matchedInvite.assignedSheetIds,
          )

          val created =
            db.collection("users")
              .document(uid)
              .get()
              .await()
              .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              ?: UserProfile(
                id = uid,
                userId = uid,
                email = normalizedEmail,
                displayName = safeDisplayName,
                photoUrl = photoUrl,
                role = UserRole.EMPLOYEE.name,
                workspaceId = matchedInvite.adminUid,
                inviteCodeUsed = redeemCode,
                addedByAdminUid = matchedInvite.adminUid,
                addedByAdminName = safeAdminName,
                addedByAdminEmail = safeAdminEmail,
                status = AccountStatus.ACTIVE.name,
              )
          return@withLock Result.success(created)
        }

        // No active invitation matched: preserve existing valid user or register new independent ADMIN
        if (existingProfile != null) {
          if (existingProfile.resolvedRole() == UserRole.EMPLOYEE) {
            if (existingEmp == null) {
              return@withLock Result.failure(
                IllegalStateException("Employee access has been removed by the workspace Admin.")
              )
            }
            if (existingEmp.resolvedStatus() != AccountStatus.ACTIVE) {
              return@withLock Result.failure(
                IllegalStateException(
                  "Your employee access is currently ${existingEmp.resolvedStatus().name}. Contact your Admin (${existingProfile.addedByAdminName ?: existingProfile.addedByAdminEmail})."
                )
              )
            }
          }
          return@withLock Result.success(existingProfile)
        }

        // Direct login without any employee invitation => independent ADMIN workspace
        val safeAdminDisplayName = displayName.trim().ifBlank { "Admin" }.take(120)
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        val userPayload =
          mapOf(
            "userId" to uid,
            "email" to normalizedEmail.take(200),
            "displayName" to safeAdminDisplayName,
            "photoUrl" to photoUrl?.take(500),
            "role" to UserRole.ADMIN.name,
            "workspaceId" to uid,
            "status" to AccountStatus.ACTIVE.name,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        batch.set(userRef, userPayload)

        val settingsRef =
          db.collection("workspaces").document(uid).collection("settings").document("config")
        batch.set(
          settingsRef,
          mapOf(
            "workspaceId" to uid,
            "autoSyncIntervalMinutes" to 15L,
            "maskAadhaarByDefault" to true,
            "allowEmployeeExport" to false,
            "duplicateImeiCheckEnabled" to true,
            "updatedByUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        batch.commit().await()

        runCatching {
          db.collection("workspaces")
            .document(uid)
            .collection("activity_logs")
            .document()
            .set(
              mapOf(
                "workspaceId" to uid,
                "userId" to uid,
                "userName" to safeAdminDisplayName,
                "userEmail" to normalizedEmail.take(200),
                "userRole" to UserRole.ADMIN.name,
                "activityType" to "ADMIN_REGISTERED",
                "description" to "Created Admin workspace for $normalizedEmail",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }

        val created =
          db.collection("users")
            .document(uid)
            .get()
            .await()
            .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: UserProfile(
              id = uid,
              userId = uid,
              email = normalizedEmail,
              displayName = safeAdminDisplayName,
              photoUrl = photoUrl,
              role = UserRole.ADMIN.name,
              workspaceId = uid,
              status = AccountStatus.ACTIVE.name,
            )
        Result.success(created)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, userPath)
        Result.failure(e)
      }
    }

  private suspend fun resolveActiveInvitationForLogin(
    cleanCode: String,
    normalizedEmail: String,
  ): Result<Invitation?> {
    // 1. Explicit invite code (deep link / manual entry)
    if (cleanCode.isNotBlank()) {
      val inviteSnap = db.collection("invitations").document(cleanCode).get().await()
      if (!inviteSnap.exists()) {
        return Result.failure(IllegalArgumentException("Invitation code '$cleanCode' was not found."))
      }
      val invite =
        inviteSnap.toObject(
          Invitation::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        ) ?: return Result.failure(IllegalArgumentException("Invalid invitation record."))
      validateInvitationEligibility(invite, normalizedEmail)?.let { err ->
        return Result.failure(IllegalStateException(err))
      }
      return Result.success(invite)
    }

    // 2. Automatic email lookup via O(1) email_invites index
    val emailIndexSnap =
      runCatching { db.collection("email_invites").document(normalizedEmail).get().await() }
        .getOrNull()
    if (emailIndexSnap != null && emailIndexSnap.exists()) {
      val indexStatus = emailIndexSnap.getString("status")
      val linkedCode = emailIndexSnap.getString("inviteCode")
      if (indexStatus == InvitationStatus.ACTIVE.name && !linkedCode.isNullOrBlank()) {
        val invSnap =
          runCatching { db.collection("invitations").document(linkedCode).get().await() }
            .getOrNull()
        val inv =
          invSnap?.toObject(
            Invitation::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        if (inv != null && validateInvitationEligibility(inv, normalizedEmail) == null) {
          return Result.success(inv)
        }
      }
    }

    // 3. Fallback query on invitations collection by normalized targetEmail
    val querySnap =
      runCatching {
        db.collection("invitations").whereEqualTo("targetEmail", normalizedEmail).get().await()
      }.getOrNull()
    if (querySnap != null) {
      val candidates =
        querySnap
          .toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .filter { validateInvitationEligibility(it, normalizedEmail) == null }
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      if (candidates.isNotEmpty()) {
        return Result.success(candidates.first())
      }
    }

    return Result.success(null)
  }

  private fun validateInvitationEligibility(invite: Invitation, normalizedEmail: String): String? {
    if (invite.resolvedStatus() != InvitationStatus.ACTIVE) {
      return "This invitation is ${invite.resolvedStatus().name.lowercase()}."
    }
    if (invite.usedCount >= invite.maxUses) {
      return "This invitation has reached its usage limit."
    }
    val expiresAt = invite.expiresAt
    if (expiresAt != null && expiresAt.toDate().time < System.currentTimeMillis()) {
      return "This invitation link has expired."
    }
    val inviteEmail = invite.normalizedTargetEmail()
    if (!inviteEmail.isNullOrBlank() && inviteEmail != normalizedEmail) {
      return "This invitation is restricted to $inviteEmail. You signed in as $normalizedEmail."
    }
    return null
  }

  suspend fun reconcileEmployeeSheetMemberships(
    workspaceId: String,
    employeeUid: String,
    assignedSheetIds: List<String>,
  ) {
    if (workspaceId.isBlank() || employeeUid.isBlank() || assignedSheetIds.isEmpty()) return
    val wsDoc = db.collection("workspaces").document(workspaceId)
    for (sheetId in assignedSheetIds.distinct()) {
      runCatching {
        val sheetRef = wsDoc.collection("sheets").document(sheetId)
        sheetRef
          .update(
            mapOf(
              "assignedEmployeeUids" to FieldValue.arrayUnion(employeeUid),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
        val sheet =
          sheetRef
            .get()
            .await()
            .toObject(SheetConnection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (sheet != null) {
          for (tabTitle in sheet.selectedWorksheets) {
            val wsDocId = SheetSyncRepository.buildWorksheetDocId(sheetId, tabTitle)
            runCatching {
              wsDoc
                .collection("worksheets")
                .document(wsDocId)
                .update(
                  mapOf(
                    "assignedEmployeeUids" to FieldValue.arrayUnion(employeeUid),
                    "updatedAt" to FieldValue.serverTimestamp(),
                  )
                )
                .await()
            }
          }
        }
      }
    }
  }

  suspend fun validateInvitationPreview(inviteCode: String): Result<Invitation> {
    val clean = inviteCode.trim().uppercase()
    if (clean.isBlank()) return Result.failure(IllegalArgumentException("Enter an invitation code."))
    return try {
      val snap = db.collection("invitations").document(clean).get().await()
      val inv =
        snap.toObject(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: return Result.failure(IllegalArgumentException("Invitation '$clean' does not exist."))
      if (inv.resolvedStatus() != InvitationStatus.ACTIVE) {
        return Result.failure(IllegalStateException("Invitation is ${inv.resolvedStatus().name}."))
      }
      if (inv.usedCount >= inv.maxUses) {
        return Result.failure(IllegalStateException("Invitation usage limit reached."))
      }
      Result.success(inv)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "invitations/$clean")
      Result.failure(e)
    }
  }

  suspend fun deleteCurrentAccount(
    credentialManager: CredentialManager,
    workspaceId: String,
    isAdmin: Boolean,
  ): Result<Unit> {
    val firebaseUser =
      auth.currentUser
        ?: return Result.failure(IllegalStateException("No authenticated user session found."))
    val uid = firebaseUser.uid
    return try {
      // Remove user's saved searches
      val savedSearches =
        db.collection("users").document(uid).collection("saved_searches").get().await()
      for (doc in savedSearches.documents) {
        doc.reference.delete().await()
      }

      if (isAdmin && workspaceId == uid) {
        val wsRef = db.collection("workspaces").document(uid)
        runCatching {
          val emailInvites =
            db.collection("email_invites").whereEqualTo("adminUid", uid).get().await()
          emailInvites.documents.forEach { it.reference.delete().await() }
        }
        runCatching {
          val invites = db.collection("invitations").whereEqualTo("adminUid", uid).get().await()
          invites.documents.forEach { it.reference.delete().await() }
        }
        runCatching {
          val worksheets = wsRef.collection("worksheets").get().await()
          worksheets.documents.forEach { it.reference.delete().await() }
        }
        runCatching {
          val sheets = wsRef.collection("sheets").get().await()
          sheets.documents.forEach { sheetDoc ->
            runCatching {
              sheetDoc.reference.collection("row_pages").get().await().documents.forEach {
                it.reference.delete().await()
              }
            }
            runCatching {
              sheetDoc.reference.collection("pending_writes").get().await().documents.forEach {
                it.reference.delete().await()
              }
            }
            sheetDoc.reference.delete().await()
          }
        }
        runCatching {
          val employees = wsRef.collection("employees").get().await()
          employees.documents.forEach { it.reference.delete().await() }
        }
        runCatching { wsRef.collection("settings").document("config").delete().await() }
      } else if (workspaceId.isNotBlank()) {
        runCatching {
          db.collection("workspaces")
            .document(workspaceId)
            .collection("employees")
            .document(uid)
            .delete()
            .await()
        }
      }

      // Delete primary user profile document
      db.collection("users").document(uid).delete().await()

      // Delete Firebase Authentication account
      firebaseUser.delete().await()

      withContext(NonCancellable) {
        try {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
          Log.w(TAG, "Unable to clear credential state after deletion: ${e.message}")
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
      Result.failure(
        IllegalStateException(
          "For security, please Sign Out and Sign In again right before deleting your account."
        )
      )
    } catch (e: Exception) {
      Log.e(TAG, "Account deletion failed", e)
      Result.failure(e)
    }
  }

  private companion object {
    const val TAG = "AuthRepository"
  }
}
