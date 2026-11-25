package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "cached_sheet_rows",
  indices = [
    Index(value = ["workspaceId"]),
    Index(value = ["sheetConnectionId"]),
    Index(value = ["imei1"]),
    Index(value = ["imei2"]),
    Index(value = ["mobileNumber"]),
  ],
)
data class CachedSheetRowEntity(
  @PrimaryKey val id: String,
  val workspaceId: String,
  val sheetConnectionId: String,
  val spreadsheetId: String,
  val spreadsheetName: String,
  val worksheetTitle: String,
  val rowNumber: Int,
  val headersJson: String,
  val cellsJson: String,
  val searchableText: String,
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
)
