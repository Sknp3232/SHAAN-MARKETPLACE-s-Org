package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ConnectionHealth {
  HEALTHY,
  SYNCING,
  ERROR,
  DISCONNECTED;

  companion object {
    fun fromRaw(raw: String?): ConnectionHealth =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HEALTHY
  }
}

enum class SyncStatus {
  IDLE,
  SYNCING,
  SUCCESS,
  ERROR;

  companion object {
    fun fromRaw(raw: String?): SyncStatus =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: IDLE
  }
}

enum class FieldInputType(val label: String) {
  TEXT("Text"),
  NUMBER("Number"),
  DATE("Date"),
  MOBILE("Mobile Number"),
  IMEI("IMEI (15 Digits)"),
  DROPDOWN("Dropdown"),
  AADHAAR("Aadhaar Number");

  companion object {
    fun fromRaw(raw: String?): FieldInputType =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TEXT

    fun inferFromHeader(header: String): FieldInputType {
      val h = header.trim().lowercase()
      return when {
        h.contains("imei") -> IMEI
        h.contains("aadhaar") || h.contains("aadhar") || h.contains("uidai") -> AADHAAR
        h.contains("mobile") || h.contains("phone") || h.contains("contact") || h.contains("whatsapp") ->
          MOBILE
        h.contains("date") || h.contains("dob") || h == "time" || h.endsWith("at") -> DATE
        h.contains("amount") ||
          h.contains("price") ||
          h.contains("emi") ||
          h.contains("downpayment") ||
          h.contains("balance") ||
          h.contains("qty") ||
          h.contains("quantity") -> NUMBER
        else -> TEXT
      }
    }
  }
}

@Keep
@IgnoreExtraProperties
data class SheetConnection(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val spreadsheetId: String = "",
  val originalTitle: String = "",
  val customName: String = "",
  val spreadsheetUrl: String? = null,
  val selectedWorksheets: List<String> = emptyList(),
  val allWorksheets: List<String> = emptyList(),
  val active: Boolean = true,
  val connectionHealth: String = ConnectionHealth.HEALTHY.name,
  val lastSyncAt: Timestamp? = null,
  val lastSyncError: String? = null,
  val totalRecords: Long = 0L,
  val aadhaarMaskingEnabled: Boolean = true,
  val assignedEmployeeUids: List<String> = emptyList(),
  val createdByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedHealth(): ConnectionHealth = ConnectionHealth.fromRaw(connectionHealth)
}

@Keep
@IgnoreExtraProperties
data class WorksheetMetadata(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val sheetConnectionId: String = "",
  val spreadsheetId: String = "",
  val worksheetTitle: String = "",
  val sheetGid: Long = 0L,
  val headers: List<String> = emptyList(),
  val requiredFields: List<String> = emptyList(),
  val fieldTypes: Map<String, String> = emptyMap(),
  val dropdownOptions: Map<String, String> = emptyMap(),
  val recordCount: Long = 0L,
  val assignedEmployeeUids: List<String> = emptyList(),
  val lastSyncAt: Timestamp? = null,
  val syncStatus: String = SyncStatus.IDLE.name,
  val lastSyncError: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedSyncStatus(): SyncStatus = SyncStatus.fromRaw(syncStatus)
}

@Keep
@IgnoreExtraProperties
data class RecordAudit(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val sheetConnectionId: String = "",
  val spreadsheetId: String = "",
  val spreadsheetName: String = "",
  val worksheetTitle: String = "",
  val rowNumber: Long = 1L,
  val actionType: String = "ADDED",
  val addedByUid: String = "",
  val addedByName: String = "",
  val addedByEmail: String = "",
  val recordSummary: String = "",
  val imeiValues: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ActivityLog(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val userId: String = "",
  val userName: String = "",
  val userEmail: String = "",
  val userRole: String = UserRole.ADMIN.name,
  val activityType: String = "",
  val description: String = "",
  val sheetId: String? = null,
  val sheetName: String? = null,
  val worksheetTitle: String? = null,
  val rowReference: String? = null,
  val searchQuery: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SyncLog(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val sheetConnectionId: String = "",
  val sheetName: String = "",
  val triggeredByUid: String = "",
  val triggeredByName: String = "",
  val status: String = "SUCCESS",
  val recordsSynced: Long = 0L,
  val errorMessage: String? = null,
  val durationMs: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WorkspaceSettings(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val autoSyncIntervalMinutes: Long = 15L,
  val maskAadhaarByDefault: Boolean = true,
  val allowEmployeeExport: Boolean = false,
  val duplicateImeiCheckEnabled: Boolean = true,
  val updatedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SavedSearch(
  @DocumentId val id: String = "",
  val userId: String = "",
  val query: String = "",
  val filtersSummary: String = "",
  val saved: Boolean = false,
  val resultCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SheetRowPage(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val sheetConnectionId: String = "",
  val spreadsheetId: String = "",
  val spreadsheetName: String = "",
  val worksheetTitle: String = "",
  val pageIndex: Long = 0L,
  val headers: List<String> = emptyList(),
  val rowsJson: String = "[]",
  val rowCount: Long = 0L,
  val assignedEmployeeUids: List<String> = emptyList(),
  val syncedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PendingSheetWrite(
  @DocumentId val id: String = "",
  val workspaceId: String = "",
  val sheetConnectionId: String = "",
  val spreadsheetId: String = "",
  val worksheetTitle: String = "",
  val actionType: String = "ADD",
  val rowNumber: Long = 0L,
  val values: List<String> = emptyList(),
  val headers: List<String> = emptyList(),
  val requestedByUid: String = "",
  val requestedByName: String = "",
  val requestedByEmail: String = "",
  val status: String = "PENDING",
  val errorMessage: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

