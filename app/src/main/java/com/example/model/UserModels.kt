package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole {
  ADMIN,
  EMPLOYEE;

  companion object {
    fun fromRaw(raw: String?): UserRole =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ADMIN
  }
}

enum class AccountStatus {
  ACTIVE,
  SUSPENDED,
  REMOVED;

  companion object {
    fun fromRaw(raw: String?): AccountStatus =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
  }
}

enum class SheetPermissionLevel(val label: String, val description: String) {
  SEARCH_ONLY("Search Only", "Search authorized sheets and view records"),
  SEARCH_ADD("Search + Add", "Search, view records, and insert new records"),
  SEARCH_EDIT("Search + Edit", "Search, view, add, and edit authorized records"),
  FULL_ACCESS("Full Employee Access", "All authorized search, add, edit, and export actions");

  fun canAdd(): Boolean = this != SEARCH_ONLY

  fun canEdit(): Boolean = this == SEARCH_EDIT || this == FULL_ACCESS

  companion object {
    fun fromRaw(raw: String?): SheetPermissionLevel =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SEARCH_ONLY
  }
}

enum class InvitationStatus {
  ACTIVE,
  REVOKED,
  EXPIRED;

  companion object {
    fun fromRaw(raw: String?): InvitationStatus =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
  }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val photoUrl: String? = null,
  val role: String = UserRole.ADMIN.name,
  val workspaceId: String = "",
  val inviteCodeUsed: String? = null,
  val addedByAdminUid: String? = null,
  val addedByAdminName: String? = null,
  val addedByAdminEmail: String? = null,
  val invitedAt: Timestamp? = null,
  val joinedAt: Timestamp? = null,
  val status: String = AccountStatus.ACTIVE.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedRole(): UserRole = UserRole.fromRaw(role)

  fun resolvedStatus(): AccountStatus = AccountStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class Invitation(
  @DocumentId val id: String = "",
  val inviteCode: String = "",
  val adminUid: String = "",
  val adminName: String = "",
  val adminEmail: String = "",
  val workspaceId: String = "",
  val targetEmail: String? = null,
  val role: String = UserRole.EMPLOYEE.name,
  val defaultPermission: String = SheetPermissionLevel.SEARCH_ADD.name,
  val assignedSheetIds: List<String> = emptyList(),
  val sheetPermissions: Map<String, String> = emptyMap(),
  val maxUses: Long = 10L,
  val usedCount: Long = 0L,
  val status: String = InvitationStatus.ACTIVE.name,
  val expiresAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedRole(): UserRole = UserRole.fromRaw(role)

  fun resolvedStatus(): InvitationStatus = InvitationStatus.fromRaw(status)

  fun resolvedPermission(): SheetPermissionLevel = SheetPermissionLevel.fromRaw(defaultPermission)

  fun normalizedTargetEmail(): String = targetEmail?.trim()?.lowercase().orEmpty()

  fun permissionForSheet(sheetId: String): SheetPermissionLevel {
    val specific = sheetPermissions[sheetId]
    return if (specific != null) {
      SheetPermissionLevel.fromRaw(specific)
    } else {
      resolvedPermission()
    }
  }
}

@Keep
@IgnoreExtraProperties
data class EmployeeRecord(
  @DocumentId val id: String = "",
  val employeeUid: String = "",
  val workspaceId: String = "",
  val employeeCode: String = "",
  val name: String = "",
  val email: String = "",
  val photoUrl: String? = null,
  val addedByAdminUid: String = "",
  val addedByAdminName: String = "",
  val addedByAdminEmail: String = "",
  val inviteCode: String = "",
  val invitedAt: Timestamp? = null,
  val joinedAt: Timestamp? = null,
  val status: String = AccountStatus.ACTIVE.name,
  val defaultPermission: String = SheetPermissionLevel.SEARCH_ONLY.name,
  val assignedSheetIds: List<String> = emptyList(),
  val sheetPermissions: Map<String, String> = emptyMap(),
  val totalSearches: Long = 0L,
  val totalRecordsAdded: Long = 0L,
  val lastActiveAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedStatus(): AccountStatus = AccountStatus.fromRaw(status)

  fun resolvedDefaultPermission(): SheetPermissionLevel =
    SheetPermissionLevel.fromRaw(defaultPermission)

  fun permissionForSheet(sheetId: String): SheetPermissionLevel {
    val specific = sheetPermissions[sheetId]
    return if (specific != null) {
      SheetPermissionLevel.fromRaw(specific)
    } else {
      resolvedDefaultPermission()
    }
  }
}
