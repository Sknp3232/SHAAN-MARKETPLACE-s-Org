package com.example.data.remote

import com.example.model.DriveSpreadsheetItem
import com.example.model.RemoteSpreadsheetPreview
import com.example.model.RemoteWorksheetPreview
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GoogleApiException(
  val httpCode: Int,
  val apiReason: String? = null,
  message: String,
) : IOException(message)

class GoogleDriveSheetsClient {
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .connectionPool(ConnectionPool(5, 3, TimeUnit.MINUTES))
      .build()

  private data class CacheEntry<T>(
    val value: T,
    val timestampMillis: Long,
  )

  private val cacheMutex = Mutex()
  private val driveListCache = LinkedHashMap<String, CacheEntry<List<DriveSpreadsheetItem>>>(16, 0.75f, true)
  private val metadataCache = LinkedHashMap<String, CacheEntry<RemoteSpreadsheetPreview>>(32, 0.75f, true)
  private val inFlightDriveLists = ConcurrentHashMap<String, CompletableDeferred<Result<List<DriveSpreadsheetItem>>>>()
  private val inFlightMetadata = ConcurrentHashMap<String, CompletableDeferred<Result<RemoteSpreadsheetPreview>>>()

  suspend fun invalidateCache() {
    cacheMutex.withLock {
      driveListCache.clear()
      metadataCache.clear()
    }
  }

  suspend fun listSpreadsheets(
    accessToken: String,
    searchQuery: String = "",
    maxPages: Int = 5,
    forceRefresh: Boolean = false,
  ): Result<List<DriveSpreadsheetItem>> {
    val cleanQuery = searchQuery.trim()
    val cacheKey = "${accessToken.hashCode()}|${cleanQuery.lowercase()}|$maxPages"

    if (!forceRefresh) {
      val cached =
        cacheMutex.withLock {
          driveListCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.timestampMillis < DRIVE_LIST_TTL_MS }
        }
      if (cached != null) return Result.success(cached.value)
    }

    val newDeferred = CompletableDeferred<Result<List<DriveSpreadsheetItem>>>()
    val existing = inFlightDriveLists.putIfAbsent(cacheKey, newDeferred)
    if (existing != null) {
      return existing.await()
    }

    val result =
      withContext(Dispatchers.IO) {
        try {
          val baseQ =
            "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
          val fullQ =
            if (cleanQuery.isNotBlank()) {
              val escaped = cleanQuery.replace("'", "\\'")
              "$baseQ and name contains '$escaped'"
            } else {
              baseQ
            }
          val encodedQ = URLEncoder.encode(fullQ, "UTF-8")
          val fields =
            URLEncoder.encode("nextPageToken,files(id,name,modifiedTime,webViewLink)", "UTF-8")

          val allItems = mutableListOf<DriveSpreadsheetItem>()
          var pageToken: String? = null
          var pageCount = 0

          do {
            pageCount++
            val pageParam =
              if (!pageToken.isNullOrBlank()) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
              else ""
            val url =
              "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=$fields&orderBy=modifiedTime%20desc&pageSize=100$pageParam"

            val body = executeWithRetry(accessToken) {
              Request.Builder().url(url).get().build()
            }
            val json = JSONObject(body)
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
              val obj = files.getJSONObject(i)
              allItems.add(
                DriveSpreadsheetItem(
                  id = obj.optString("id"),
                  name = obj.optString("name"),
                  modifiedTime = obj.optString("modifiedTime"),
                  webViewLink = obj.optString("webViewLink"),
                )
              )
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
          } while (pageToken != null && pageCount < maxPages)

          cacheMutex.withLock {
            if (driveListCache.size >= MAX_DRIVE_CACHE_ENTRIES) {
              val oldest = driveListCache.entries.firstOrNull()?.key
              if (oldest != null) driveListCache.remove(oldest)
            }
            driveListCache[cacheKey] = CacheEntry(allItems, System.currentTimeMillis())
          }

          Result.success(allItems)
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

    inFlightDriveLists.remove(cacheKey, newDeferred)
    newDeferred.complete(result)
    return result
  }

  suspend fun createSpreadsheet(
    accessToken: String,
    title: String,
    initialWorksheetTitle: String = "Records",
    initialHeaders: List<String> = emptyList(),
  ): Result<RemoteSpreadsheetPreview> =
    withContext(Dispatchers.IO) {
      try {
        val cleanTitle = title.trim().ifBlank { "Smart Sheet Ledger" }.take(200)
        val cleanTab = initialWorksheetTitle.trim().ifBlank { "Records" }.take(100)
        val payload =
          JSONObject().apply {
            put("properties", JSONObject().apply { put("title", cleanTitle) })
            put(
              "sheets",
              JSONArray().apply {
                put(
                  JSONObject().apply {
                    put("properties", JSONObject().apply { put("title", cleanTab) })
                  }
                )
              },
            )
          }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val url = "https://sheets.googleapis.com/v4/spreadsheets"
        val body = executeWithRetry(accessToken) {
          Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(mediaType))
            .build()
        }
        val json = JSONObject(body)
        val createdId = json.getString("spreadsheetId")
        val sheetUrl = json.optString("spreadsheetUrl")
        val sheetsArr = json.optJSONArray("sheets") ?: JSONArray()
        val worksheets =
          List(sheetsArr.length()) { i ->
            val props = sheetsArr.getJSONObject(i).getJSONObject("properties")
            val grid = props.optJSONObject("gridProperties")
            RemoteWorksheetPreview(
              sheetId = props.optLong("sheetId"),
              title = props.optString("title"),
              rowCount = grid?.optInt("rowCount") ?: 1000,
              columnCount = grid?.optInt("columnCount") ?: 26,
            )
          }

        val cleanHeaders = initialHeaders.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanHeaders.isNotEmpty()) {
          updateRow(
            accessToken = accessToken,
            spreadsheetId = createdId,
            worksheetTitle = cleanTab,
            rowNumber = 1,
            values = cleanHeaders,
          )
        }

        invalidateCache()
        Result.success(
          RemoteSpreadsheetPreview(
            spreadsheetId = createdId,
            title = json.optJSONObject("properties")?.optString("title") ?: cleanTitle,
            spreadsheetUrl = sheetUrl,
            worksheets = worksheets,
          )
        )
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun getSpreadsheetMetadata(
    accessToken: String,
    spreadsheetId: String,
    forceRefresh: Boolean = false,
  ): Result<RemoteSpreadsheetPreview> {
    val cleanId = extractSpreadsheetId(spreadsheetId)
    val cacheKey = "${accessToken.hashCode()}|$cleanId"

    if (!forceRefresh) {
      val cached =
        cacheMutex.withLock {
          metadataCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.timestampMillis < METADATA_TTL_MS }
        }
      if (cached != null) return Result.success(cached.value)
    }

    val newDeferred = CompletableDeferred<Result<RemoteSpreadsheetPreview>>()
    val existing = inFlightMetadata.putIfAbsent(cacheKey, newDeferred)
    if (existing != null) {
      return existing.await()
    }

    val result =
      withContext(Dispatchers.IO) {
        try {
          val fields =
            URLEncoder.encode(
              "spreadsheetId,properties.title,spreadsheetUrl,sheets.properties(sheetId,title,gridProperties(rowCount,columnCount))",
              "UTF-8",
            )
          val url = "https://sheets.googleapis.com/v4/spreadsheets/$cleanId?fields=$fields"
          val body = executeWithRetry(accessToken) {
            Request.Builder().url(url).get().build()
          }
          val json = JSONObject(body)
          val title = json.optJSONObject("properties")?.optString("title") ?: "Spreadsheet"
          val sheetUrl = json.optString("spreadsheetUrl")
          val sheetsArr = json.optJSONArray("sheets") ?: JSONArray()
          val worksheets =
            List(sheetsArr.length()) { i ->
              val props = sheetsArr.getJSONObject(i).getJSONObject("properties")
              val grid = props.optJSONObject("gridProperties")
              RemoteWorksheetPreview(
                sheetId = props.optLong("sheetId"),
                title = props.optString("title"),
                rowCount = grid?.optInt("rowCount") ?: 0,
                columnCount = grid?.optInt("columnCount") ?: 0,
              )
            }
          val preview =
            RemoteSpreadsheetPreview(
              spreadsheetId = json.optString("spreadsheetId", cleanId),
              title = title,
              spreadsheetUrl = sheetUrl,
              worksheets = worksheets,
            )
          cacheMutex.withLock {
            if (metadataCache.size >= MAX_METADATA_CACHE_ENTRIES) {
              val oldest = metadataCache.entries.firstOrNull()?.key
              if (oldest != null) metadataCache.remove(oldest)
            }
            metadataCache[cacheKey] = CacheEntry(preview, System.currentTimeMillis())
          }
          Result.success(preview)
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

    inFlightMetadata.remove(cacheKey, newDeferred)
    newDeferred.complete(result)
    return result
  }

  suspend fun batchGetWorksheetValues(
    accessToken: String,
    spreadsheetId: String,
    worksheetTitles: List<String>,
  ): Result<Map<String, List<List<String>>>> =
    withContext(Dispatchers.IO) {
      try {
        if (worksheetTitles.isEmpty()) return@withContext Result.success(emptyMap())
        val cleanId = extractSpreadsheetId(spreadsheetId)
        val result = linkedMapOf<String, MutableList<List<String>>>()
        worksheetTitles.forEach { result[it] = mutableListOf() }

        var activeTabs = worksheetTitles
        var startRow = 1
        var pageIndex = 0

        // Paginate across large sheets in chunks of ROW_PAGE_SIZE rows
        while (activeTabs.isNotEmpty() && pageIndex < MAX_ROW_PAGES) {
          val endRow = startRow + ROW_PAGE_SIZE - 1
          val rangeParams =
            activeTabs.joinToString("&") { title ->
              val safeRange = "'${title.replace("'", "''")}'!A$startRow:ZZ$endRow"
              "ranges=${URLEncoder.encode(safeRange, "UTF-8")}"
            }
          val url =
            "https://sheets.googleapis.com/v4/spreadsheets/$cleanId/values:batchGet?$rangeParams&majorDimension=ROWS&valueRenderOption=FORMATTED_VALUE"

          val body = executeWithRetry(accessToken) {
            Request.Builder().url(url).get().build()
          }
          val json = JSONObject(body)
          val valueRanges = json.optJSONArray("valueRanges") ?: JSONArray()
          val nextTabs = mutableListOf<String>()

          for (i in 0 until valueRanges.length()) {
            val rangeObj = valueRanges.getJSONObject(i)
            val title = activeTabs.getOrNull(i) ?: continue
            val valuesArr = rangeObj.optJSONArray("values") ?: JSONArray()
            val rows =
              List(valuesArr.length()) { r ->
                val rowArr = valuesArr.optJSONArray(r) ?: JSONArray()
                List(rowArr.length()) { c -> rowArr.optString(c, "") }
              }
            result[title]?.addAll(rows)
            if (rows.size >= ROW_PAGE_SIZE) {
              nextTabs.add(title)
            }
          }

          activeTabs = nextTabs
          startRow = endRow + 1
          pageIndex++
        }

        Result.success(result.mapValues { it.value.toList() })
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun appendRow(
    accessToken: String,
    spreadsheetId: String,
    worksheetTitle: String,
    values: List<String>,
  ): Result<Int> =
    withContext(Dispatchers.IO) {
      try {
        val cleanId = extractSpreadsheetId(spreadsheetId)
        val encodedRange =
          URLEncoder.encode("'${worksheetTitle.replace("'", "''")}'!A1", "UTF-8")
        val url =
          "https://sheets.googleapis.com/v4/spreadsheets/$cleanId/values/$encodedRange:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS&includeValuesInResponse=false"

        val payload =
          JSONObject().apply {
            put("majorDimension", "ROWS")
            put("values", JSONArray().apply { put(JSONArray(values)) })
          }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = executeWithRetry(accessToken) {
          Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(mediaType))
            .build()
        }
        val json = JSONObject(body)
        val updatedRange =
          json.optJSONObject("updates")?.optString("updatedRange").orEmpty()
        val insertedRow = parseRowNumberFromA1Range(updatedRange)
        cacheMutex.withLock {
          metadataCache.keys.removeAll { it.endsWith("|$cleanId") }
        }
        Result.success(insertedRow)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun updateRow(
    accessToken: String,
    spreadsheetId: String,
    worksheetTitle: String,
    rowNumber: Int,
    values: List<String>,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val cleanId = extractSpreadsheetId(spreadsheetId)
        val range = "'${worksheetTitle.replace("'", "''")}'!A$rowNumber"
        val encodedRange = URLEncoder.encode(range, "UTF-8")
        val url =
          "https://sheets.googleapis.com/v4/spreadsheets/$cleanId/values/$encodedRange?valueInputOption=USER_ENTERED"

        val payload =
          JSONObject().apply {
            put("range", range)
            put("majorDimension", "ROWS")
            put("values", JSONArray().apply { put(JSONArray(values)) })
          }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        executeWithRetry(accessToken) {
          Request.Builder()
            .url(url)
            .put(payload.toString().toRequestBody(mediaType))
            .build()
        }
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun deleteRow(
    accessToken: String,
    spreadsheetId: String,
    sheetGid: Long,
    rowNumber: Int,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        if (rowNumber < 2) {
          return@withContext Result.failure(
            IllegalArgumentException("Header row #1 cannot be deleted.")
          )
        }
        val cleanId = extractSpreadsheetId(spreadsheetId)
        val startIndex = rowNumber - 1
        val endIndex = rowNumber
        val payload =
          JSONObject().apply {
            put(
              "requests",
              JSONArray().apply {
                put(
                  JSONObject().apply {
                    put(
                      "deleteDimension",
                      JSONObject().apply {
                        put(
                          "range",
                          JSONObject().apply {
                            put("sheetId", sheetGid)
                            put("dimension", "ROWS")
                            put("startIndex", startIndex)
                            put("endIndex", endIndex)
                          },
                        )
                      },
                    )
                  }
                )
              },
            )
          }
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$cleanId:batchUpdate"
        executeWithRetry(accessToken) {
          Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(mediaType))
            .build()
        }
        cacheMutex.withLock {
          metadataCache.keys.removeAll { it.endsWith("|$cleanId") }
        }
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private suspend fun executeWithRetry(
    accessToken: String,
    buildRequest: () -> Request,
  ): String {
    var attempt = 0
    var backoffMs = 600L
    while (true) {
      attempt++
      val request =
        buildRequest()
          .newBuilder()
          .addHeader("Authorization", "Bearer $accessToken")
          .addHeader("Accept", "application/json")
          .build()

      client.newCall(request).execute().use { response ->
        val rawBody = response.body?.string().orEmpty()
        if (response.isSuccessful) {
          return rawBody
        }
        if ((response.code == 429 || response.code == 503) && attempt < 3) {
          delay(backoffMs)
          backoffMs *= 2
          return@use
        }
        val errorObj =
          try {
            JSONObject(rawBody).optJSONObject("error")
          } catch (_: Exception) {
            null
          }
        val apiMessage = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
        val apiReason =
          errorObj?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
            ?.takeIf { it.isNotBlank() }

        val msg =
          when (response.code) {
            401 -> "Google OAuth token expired or revoked. Attempting refresh or reconnect required."
            403 -> {
              if (apiReason == "rateLimitExceeded" || apiReason == "userRateLimitExceeded") {
                "Google API quota exceeded. Please wait a moment and try again."
              } else if (apiReason == "insufficientPermissions") {
                "Missing required Google Drive/Sheets permissions. Reconnect Google and grant all requested scopes."
              } else if (apiReason == "accessNotConfigured") {
                "Google Drive or Sheets API is not enabled in the Google Cloud project."
              } else {
                GoogleOAuthErrorMapper.sanitizeErrorMessage(
                  apiMessage ?: "Permission denied by Google Drive/Sheets API (403)."
                )
              }
            }
            404 -> "Spreadsheet or worksheet not found (check the URL/ID or sharing permissions)."
            429 -> "Google Sheets API rate limit exceeded. Please retry shortly."
            else ->
              GoogleOAuthErrorMapper.sanitizeErrorMessage(
                apiMessage ?: "Google API error (${response.code})"
              )
          }
        throw GoogleApiException(httpCode = response.code, apiReason = apiReason, message = msg)
      }
    }
  }

  companion object {
    const val ROW_PAGE_SIZE = 2500
    const val MAX_ROW_PAGES = 20
    private const val DRIVE_LIST_TTL_MS = 45_000L
    private const val METADATA_TTL_MS = 60_000L
    private const val MAX_DRIVE_CACHE_ENTRIES = 16
    private const val MAX_METADATA_CACHE_ENTRIES = 32

    fun extractSpreadsheetId(input: String): String {
      val trimmed = input.trim()
      val regex = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
      return regex.find(trimmed)?.groupValues?.getOrNull(1) ?: trimmed
    }

    fun parseRowNumberFromA1Range(a1Range: String): Int {
      val afterBang = a1Range.substringAfter("!", a1Range)
      val firstCell = afterBang.substringBefore(":")
      val digits = firstCell.filter { it.isDigit() }
      return digits.toIntOrNull() ?: 2
    }

    fun calculateRowChunkRange(tabTitle: String, pageIndex: Int): String {
      val start = pageIndex * ROW_PAGE_SIZE + 1
      val end = start + ROW_PAGE_SIZE - 1
      return "'${tabTitle.replace("'", "''")}'!A$start:ZZ$end"
    }
  }
}
