package com.example.model

data class SheetRecord(
  val id: String,
  val workspaceId: String,
  val sheetConnectionId: String,
  val spreadsheetId: String,
  val spreadsheetName: String,
  val worksheetTitle: String,
  val rowNumber: Int,
  val headers: List<String>,
  val cells: List<String>,
  val customerName: String,
  val mobileNumber: String,
  val imei1: String,
  val imei2: String,
  val aadhaarNumber: String,
  val retailerName: String,
  val invoiceNumber: String,
  val productModel: String,
  val financeInfo: String,
  val recordDate: String,
  val syncedAtMillis: Long,
  val isFromCache: Boolean,
  val matchedColumns: List<String> = emptyList(),
  val appAudit: RecordAudit? = null,
) {
  fun displayAadhaar(maskByDefault: Boolean, revealFull: Boolean): String {
    val clean = aadhaarNumber.filter { it.isDigit() }
    if (clean.isEmpty()) return aadhaarNumber
    if (!maskByDefault || revealFull) return aadhaarNumber
    return if (clean.length >= 4) {
      "XXXX-XXXX-${clean.takeLast(4)}"
    } else {
      "XXXX"
    }
  }

  fun cellMap(): List<Pair<String, String>> {
    return headers.mapIndexed { idx, header ->
      header to (cells.getOrNull(idx) ?: "")
    }
  }
}

data class SearchFilters(
  val sheetConnectionId: String? = null,
  val worksheetTitle: String? = null,
  val dateQuery: String = "",
  val addedByUid: String? = null,
  val customerQuery: String = "",
  val mobileQuery: String = "",
  val imeiQuery: String = "",
  val retailerQuery: String = "",
  val modelQuery: String = "",
  val columnHeader: String? = null,
  val columnValueQuery: String = "",
  val sortAscending: Boolean = false,
  val onlyAppAdded: Boolean = false,
) {
  fun isAnyActive(): Boolean =
    sheetConnectionId != null ||
      worksheetTitle != null ||
      dateQuery.isNotBlank() ||
      addedByUid != null ||
      customerQuery.isNotBlank() ||
      mobileQuery.isNotBlank() ||
      imeiQuery.isNotBlank() ||
      retailerQuery.isNotBlank() ||
      modelQuery.isNotBlank() ||
      (columnHeader != null && columnValueQuery.isNotBlank()) ||
      onlyAppAdded

  fun summary(): String {
    val parts = mutableListOf<String>()
    if (sheetConnectionId != null) parts.add("Sheet filtered")
    if (worksheetTitle != null) parts.add("Tab: $worksheetTitle")
    if (dateQuery.isNotBlank()) parts.add("Date: $dateQuery")
    if (addedByUid != null) parts.add("Employee filtered")
    if (customerQuery.isNotBlank()) parts.add("Customer: $customerQuery")
    if (mobileQuery.isNotBlank()) parts.add("Mobile: $mobileQuery")
    if (imeiQuery.isNotBlank()) parts.add("IMEI: $imeiQuery")
    if (retailerQuery.isNotBlank()) parts.add("Retailer: $retailerQuery")
    if (modelQuery.isNotBlank()) parts.add("Model: $modelQuery")
    if (columnHeader != null && columnValueQuery.isNotBlank()) {
      parts.add("$columnHeader: $columnValueQuery")
    }
    if (onlyAppAdded) parts.add("App-added only")
    return if (parts.isEmpty()) "All records" else parts.joinToString(" • ")
  }
}

data class DriveSpreadsheetItem(
  val id: String,
  val name: String,
  val modifiedTime: String = "",
  val webViewLink: String = "",
)

data class RemoteWorksheetPreview(
  val sheetId: Long,
  val title: String,
  val rowCount: Int,
  val columnCount: Int,
)

data class RemoteSpreadsheetPreview(
  val spreadsheetId: String,
  val title: String,
  val spreadsheetUrl: String,
  val worksheets: List<RemoteWorksheetPreview>,
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}
