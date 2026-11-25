package com.example.util

import com.example.data.local.CachedSheetRowEntity
import com.example.model.RecordAudit
import com.example.model.SearchFilters
import com.example.model.SheetRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray

object SearchEngine {

  fun entityToRecord(
    entity: CachedSheetRowEntity,
    auditsByKey: Map<String, RecordAudit>,
    matchedColumns: List<String> = emptyList(),
  ): SheetRecord {
    val headers = parseHeadersCached(entity.headersJson)
    val cells = parseJsonArray(entity.cellsJson)
    val key = "${entity.sheetConnectionId}|${entity.worksheetTitle}|${entity.rowNumber}"
    return SheetRecord(
      id = entity.id,
      workspaceId = entity.workspaceId,
      sheetConnectionId = entity.sheetConnectionId,
      spreadsheetId = entity.spreadsheetId,
      spreadsheetName = entity.spreadsheetName,
      worksheetTitle = entity.worksheetTitle,
      rowNumber = entity.rowNumber,
      headers = headers,
      cells = cells,
      customerName = entity.customerName,
      mobileNumber = entity.mobileNumber,
      imei1 = entity.imei1,
      imei2 = entity.imei2,
      aadhaarNumber = entity.aadhaarNumber,
      retailerName = entity.retailerName,
      invoiceNumber = entity.invoiceNumber,
      productModel = entity.productModel,
      financeInfo = entity.financeInfo,
      recordDate = entity.recordDate,
      syncedAtMillis = entity.syncedAtMillis,
      isFromCache = entity.isFromCache,
      matchedColumns = matchedColumns,
      appAudit = auditsByKey[key],
    )
  }

  fun searchAndFilter(
    rows: List<CachedSheetRowEntity>,
    query: String,
    filters: SearchFilters,
    audits: List<RecordAudit>,
  ): List<SheetRecord> {
    val auditsByKey =
      audits
        .groupBy { "${it.sheetConnectionId}|${it.worksheetTitle}|${it.rowNumber}" }
        .mapValues { (_, list) -> list.first() }

    val tokens =
      query
        .trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    val results = ArrayList<SheetRecord>(minOf(rows.size, 200))
    val seenIds = HashSet<String>()

    for (row in rows) {
      if (!seenIds.add(row.id)) continue

      if (filters.sheetConnectionId != null && row.sheetConnectionId != filters.sheetConnectionId) {
        continue
      }
      if (filters.worksheetTitle != null &&
        !row.worksheetTitle.equals(filters.worksheetTitle, ignoreCase = true)
      ) {
        continue
      }
      if (filters.dateQuery.isNotBlank() &&
        !row.recordDate.contains(filters.dateQuery.trim(), ignoreCase = true)
      ) {
        continue
      }
      if (filters.customerQuery.isNotBlank() &&
        !row.customerName.contains(filters.customerQuery.trim(), ignoreCase = true)
      ) {
        continue
      }
      if (filters.mobileQuery.isNotBlank() &&
        !row.mobileNumber.contains(filters.mobileQuery.trim(), ignoreCase = true)
      ) {
        continue
      }
      if (filters.imeiQuery.isNotBlank()) {
        val q = filters.imeiQuery.trim()
        if (!row.imei1.contains(q, ignoreCase = true) && !row.imei2.contains(q, ignoreCase = true)) {
          continue
        }
      }
      if (filters.retailerQuery.isNotBlank() &&
        !row.retailerName.contains(filters.retailerQuery.trim(), ignoreCase = true)
      ) {
        continue
      }
      if (filters.modelQuery.isNotBlank() &&
        !row.productModel.contains(filters.modelQuery.trim(), ignoreCase = true)
      ) {
        continue
      }

      val key = "${row.sheetConnectionId}|${row.worksheetTitle}|${row.rowNumber}"
      val audit = auditsByKey[key]
      if (filters.addedByUid != null && audit?.addedByUid != filters.addedByUid) {
        continue
      }
      if (filters.onlyAppAdded && audit == null) {
        continue
      }

      if (tokens.isNotEmpty()) {
        val allMatched = tokens.all { token -> row.searchableText.contains(token) }
        if (!allMatched) continue
      }

      val headers = parseHeadersCached(row.headersJson)
      val cells = parseJsonArray(row.cellsJson)

      if (filters.columnHeader != null && filters.columnValueQuery.isNotBlank()) {
        val colIdx = headers.indexOfFirst { it.equals(filters.columnHeader, ignoreCase = true) }
        if (colIdx < 0) continue
        val cellVal = cells.getOrNull(colIdx).orEmpty()
        if (!cellVal.contains(filters.columnValueQuery.trim(), ignoreCase = true)) continue
      }

      val matchedCols =
        if (tokens.isEmpty()) {
          emptyList()
        } else {
          headers.mapIndexedNotNull { idx, header ->
            val cell = cells.getOrNull(idx).orEmpty().lowercase()
            if (tokens.any { token -> cell.contains(token) }) header else null
          }
        }

      results.add(
        SheetRecord(
          id = row.id,
          workspaceId = row.workspaceId,
          sheetConnectionId = row.sheetConnectionId,
          spreadsheetId = row.spreadsheetId,
          spreadsheetName = row.spreadsheetName,
          worksheetTitle = row.worksheetTitle,
          rowNumber = row.rowNumber,
          headers = headers,
          cells = cells,
          customerName = row.customerName,
          mobileNumber = row.mobileNumber,
          imei1 = row.imei1,
          imei2 = row.imei2,
          aadhaarNumber = row.aadhaarNumber,
          retailerName = row.retailerName,
          invoiceNumber = row.invoiceNumber,
          productModel = row.productModel,
          financeInfo = row.financeInfo,
          recordDate = row.recordDate,
          syncedAtMillis = row.syncedAtMillis,
          isFromCache = row.isFromCache,
          matchedColumns = matchedCols,
          appAudit = audit,
        )
      )
    }

    return if (filters.sortAscending) {
      results.sortedWith(compareBy<SheetRecord> { it.rowNumber }.thenBy { it.spreadsheetName })
    } else {
      results.sortedWith(compareByDescending<SheetRecord> { it.syncedAtMillis }.thenByDescending { it.rowNumber })
    }
  }

  fun buildSuggestions(rows: List<CachedSheetRowEntity>, query: String): List<String> {
    val clean = query.trim().lowercase()
    if (clean.length < 2) return emptyList()
    val suggestions = LinkedHashSet<String>()
    for (row in rows) {
      val candidates =
        listOf(
          row.customerName,
          row.mobileNumber,
          row.imei1,
          row.imei2,
          row.invoiceNumber,
          row.productModel,
          row.retailerName,
        )
      for (c in candidates) {
        if (c.isNotBlank() && c.lowercase().contains(clean)) {
          suggestions.add(c.trim())
          if (suggestions.size >= 8) return suggestions.toList()
        }
      }
    }
    return suggestions.toList()
  }

  fun isValidImei15(value: String): Boolean {
    val digits = value.trim()
    return digits.length == 15 && digits.all { it.isDigit() }
  }

  fun formatRelativeTime(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Not synced yet"
    val diff = System.currentTimeMillis() - millis
    return when {
      diff < 60_000L -> "Just now"
      diff < 3_600_000L -> "${diff / 60_000L}m ago"
      diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
      else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(millis))
    }
  }

  fun formatTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "—"
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
  }

  fun recordsToCsv(records: List<SheetRecord>, maskAadhaar: Boolean): String {
    if (records.isEmpty()) return ""
    val sb = StringBuilder()
    val headers =
      listOf("Spreadsheet", "Worksheet", "Row #") + records.first().headers + listOf("Added By", "Synced At")
    sb.appendLine(headers.joinToString(",") { escapeCsv(it) })
    for (rec in records) {
      val rowVals =
        listOf(rec.spreadsheetName, rec.worksheetTitle, rec.rowNumber.toString()) +
          rec.headers.mapIndexed { idx, h ->
            val raw = rec.cells.getOrNull(idx).orEmpty()
            if (maskAadhaar && h.lowercase().contains("aadhaar")) {
              rec.displayAadhaar(maskByDefault = true, revealFull = false)
            } else {
              raw
            }
          } +
          listOf(rec.appAudit?.addedByName ?: "Google Sheets", formatTimestamp(rec.syncedAtMillis))
      sb.appendLine(rowVals.joinToString(",") { escapeCsv(it) })
    }
    return sb.toString()
  }

  private fun escapeCsv(value: String): String {
    val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
  }

  private const val MAX_HEADER_CACHE = 64
  private val headersCache =
    object : LinkedHashMap<String, List<String>>(MAX_HEADER_CACHE, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
        size > MAX_HEADER_CACHE
    }

  private fun parseHeadersCached(json: String): List<String> {
    synchronized(headersCache) {
      headersCache[json]?.let { return it }
    }
    val parsed = parseJsonArray(json)
    synchronized(headersCache) {
      headersCache[json] = parsed
    }
    return parsed
  }

  private fun parseJsonArray(json: String): List<String> {
    return try {
      val arr = JSONArray(json)
      List(arr.length()) { i -> arr.optString(i, "") }
    } catch (_: Exception) {
      emptyList()
    }
  }
}
