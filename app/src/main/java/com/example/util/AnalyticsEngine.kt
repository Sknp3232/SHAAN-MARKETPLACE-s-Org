package com.example.util

import com.example.data.local.CachedSheetRowEntity
import com.example.model.RecordAudit
import com.example.model.SheetConnection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyEntryPoint(
  val dayKey: String,
  val label: String,
  val count: Int,
)

data class CategoryPerformanceItem(
  val name: String,
  val count: Int,
  val sharePercent: Int,
)

enum class AnalyticsCategoryDimension(val label: String) {
  MODELS("Top Models"),
  RETAILERS("Top Retailers"),
  SHEETS("Top Sheets"),
}

data class AnalyticsSummary(
  val dailyEntries: List<DailyEntryPoint> = emptyList(),
  val totalEntriesInWindow: Int = 0,
  val peakDayLabel: String = "—",
  val peakDayCount: Int = 0,
  val todayCount: Int = 0,
  val topModels: List<CategoryPerformanceItem> = emptyList(),
  val topRetailers: List<CategoryPerformanceItem> = emptyList(),
  val topSheets: List<CategoryPerformanceItem> = emptyList(),
) {
  fun categoriesFor(dimension: AnalyticsCategoryDimension): List<CategoryPerformanceItem> =
    when (dimension) {
      AnalyticsCategoryDimension.MODELS -> topModels
      AnalyticsCategoryDimension.RETAILERS -> topRetailers
      AnalyticsCategoryDimension.SHEETS -> topSheets
    }
}

object AnalyticsEngine {
  private val ISO_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  private val SHORT_LABEL = SimpleDateFormat("dd MMM", Locale.US)
  private val CANDIDATE_DATE_FORMATS =
    listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy/MM/dd", "dd MMM yyyy")

  fun computeAnalyticsSummary(
    rows: List<CachedSheetRowEntity>,
    audits: List<RecordAudit>,
    sheets: List<SheetConnection>,
    windowDays: Int = 7,
    nowMillis: Long = System.currentTimeMillis(),
  ): AnalyticsSummary {
    val safeDays = windowDays.coerceIn(3, 30)
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val dayKeys = ArrayList<Pair<String, String>>(safeDays)
    val countsByDay = LinkedHashMap<String, Int>(safeDays)
    cal.add(Calendar.DAY_OF_YEAR, -(safeDays - 1))
    for (i in 0 until safeDays) {
      val date = cal.time
      val key = synchronized(ISO_DATE) { ISO_DATE.format(date) }
      val label = synchronized(SHORT_LABEL) { SHORT_LABEL.format(date) }
      dayKeys.add(key to label)
      countsByDay[key] = 0
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    val todayKey = dayKeys.lastOrNull()?.first

    // Track audits by day (ADDED/EDITED) + cached rows whose recordDate falls in window
    val countedAuditKeys = HashSet<String>(audits.size)
    for (audit in audits) {
      if (audit.actionType.equals("DELETED", ignoreCase = true)) continue
      val ts = audit.createdAt?.toDate()?.time ?: continue
      val key = synchronized(ISO_DATE) { ISO_DATE.format(Date(ts)) }
      if (countsByDay.containsKey(key)) {
        countsByDay[key] = (countsByDay[key] ?: 0) + 1
        countedAuditKeys.add("${audit.sheetConnectionId}|${audit.worksheetTitle}|${audit.rowNumber}")
      }
    }

    for (row in rows) {
      val rowKey = "${row.sheetConnectionId}|${row.worksheetTitle}|${row.rowNumber}"
      if (rowKey in countedAuditKeys) continue
      val parsedKey = parseRecordDateToIso(row.recordDate)
      if (parsedKey != null && countsByDay.containsKey(parsedKey)) {
        countsByDay[parsedKey] = (countsByDay[parsedKey] ?: 0) + 1
      } else if (row.recordDate.isBlank() && row.syncedAtMillis > 0L) {
        val syncedKey = synchronized(ISO_DATE) { ISO_DATE.format(Date(row.syncedAtMillis)) }
        if (countsByDay.containsKey(syncedKey)) {
          countsByDay[syncedKey] = (countsByDay[syncedKey] ?: 0) + 1
        }
      }
    }

    val dailyPoints =
      dayKeys.map { (key, label) ->
        DailyEntryPoint(dayKey = key, label = label, count = countsByDay[key] ?: 0)
      }
    val totalWindow = dailyPoints.sumOf { it.count }
    val peak = dailyPoints.maxByOrNull { it.count }
    val todayCount = if (todayKey != null) countsByDay[todayKey] ?: 0 else 0

    // Top-performing categories
    val topModels = rankCategories(rows.mapNotNull { it.productModel.trim().takeIf { v -> v.isNotBlank() } })
    val topRetailers = rankCategories(rows.mapNotNull { it.retailerName.trim().takeIf { v -> v.isNotBlank() } })
    val topSheets =
      if (rows.isNotEmpty()) {
        rankCategories(rows.mapNotNull { it.spreadsheetName.trim().takeIf { v -> v.isNotBlank() } })
      } else {
        val totalFromSheets = sheets.sumOf { it.totalRecords.toInt() }.coerceAtLeast(1)
        sheets
          .sortedByDescending { it.totalRecords }
          .take(6)
          .map { s ->
            val count = s.totalRecords.toInt()
            CategoryPerformanceItem(
              name = s.customName,
              count = count,
              sharePercent = ((count * 100f) / totalFromSheets).toInt().coerceIn(0, 100),
            )
          }
      }

    return AnalyticsSummary(
      dailyEntries = dailyPoints,
      totalEntriesInWindow = totalWindow,
      peakDayLabel = peak?.takeIf { it.count > 0 }?.label ?: "—",
      peakDayCount = peak?.count ?: 0,
      todayCount = todayCount,
      topModels = topModels,
      topRetailers = topRetailers,
      topSheets = topSheets,
    )
  }

  private fun rankCategories(values: List<String>, maxItems: Int = 6): List<CategoryPerformanceItem> {
    if (values.isEmpty()) return emptyList()
    val grouped = LinkedHashMap<String, Int>()
    val displayNames = HashMap<String, String>()
    for (raw in values) {
      val norm = raw.trim().lowercase()
      if (norm.isBlank()) continue
      displayNames.putIfAbsent(norm, raw.trim())
      grouped[norm] = (grouped[norm] ?: 0) + 1
    }
    val total = values.size.coerceAtLeast(1)
    return grouped.entries
      .sortedByDescending { it.value }
      .take(maxItems)
      .map { (norm, count) ->
        CategoryPerformanceItem(
          name = displayNames[norm] ?: norm,
          count = count,
          sharePercent = ((count * 100f) / total).toInt().coerceIn(1, 100),
        )
      }
  }

  fun parseRecordDateToIso(raw: String): String? {
    val clean = raw.trim()
    if (clean.isBlank()) return null
    for (pattern in CANDIDATE_DATE_FORMATS) {
      try {
        val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
        val parsed = sdf.parse(clean.take(20)) ?: continue
        return synchronized(ISO_DATE) { ISO_DATE.format(parsed) }
      } catch (_: Exception) {
        // try next format
      }
    }
    return null
  }
}
