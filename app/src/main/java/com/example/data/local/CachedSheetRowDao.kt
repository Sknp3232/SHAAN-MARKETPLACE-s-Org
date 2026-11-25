package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedSheetRowDao {
  @Query("SELECT * FROM cached_sheet_rows WHERE workspaceId = :workspaceId ORDER BY rowNumber DESC")
  fun observeRowsForWorkspace(workspaceId: String): Flow<List<CachedSheetRowEntity>>

  @Query(
    "SELECT * FROM cached_sheet_rows WHERE workspaceId = :workspaceId AND sheetConnectionId IN (:sheetIds) ORDER BY rowNumber DESC"
  )
  fun observeRowsForSheets(workspaceId: String, sheetIds: List<String>): Flow<List<CachedSheetRowEntity>>

  @Query(
    "SELECT * FROM cached_sheet_rows WHERE workspaceId = :workspaceId AND (imei1 = :imei OR imei2 = :imei) LIMIT 5"
  )
  suspend fun findDuplicateImei(workspaceId: String, imei: String): List<CachedSheetRowEntity>

  @Query(
    "SELECT * FROM cached_sheet_rows WHERE workspaceId = :workspaceId AND sheetConnectionId = :sheetConnectionId AND worksheetTitle = :worksheetTitle ORDER BY rowNumber ASC"
  )
  suspend fun getWorksheetRows(
    workspaceId: String,
    sheetConnectionId: String,
    worksheetTitle: String,
  ): List<CachedSheetRowEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(rows: List<CachedSheetRowEntity>)

  @Query("DELETE FROM cached_sheet_rows WHERE sheetConnectionId = :sheetConnectionId")
  suspend fun deleteBySheet(sheetConnectionId: String)

  @Query(
    "DELETE FROM cached_sheet_rows WHERE sheetConnectionId = :sheetConnectionId AND worksheetTitle = :worksheetTitle"
  )
  suspend fun deleteByWorksheet(sheetConnectionId: String, worksheetTitle: String)

  @Transaction
  suspend fun replaceWorksheetRows(
    sheetConnectionId: String,
    worksheetTitle: String,
    rows: List<CachedSheetRowEntity>,
  ) {
    deleteByWorksheet(sheetConnectionId, worksheetTitle)
    if (rows.isNotEmpty()) {
      rows.chunked(400).forEach { batch ->
        upsertAll(batch)
      }
    }
  }

  @Query(
    "DELETE FROM cached_sheet_rows WHERE workspaceId = :workspaceId AND sheetConnectionId NOT IN (:allowedSheetIds)"
  )
  suspend fun deleteRowsNotInSheets(workspaceId: String, allowedSheetIds: List<String>)

  @Query("UPDATE cached_sheet_rows SET isFromCache = 1 WHERE sheetConnectionId = :sheetConnectionId")
  suspend fun markSheetRowsCached(sheetConnectionId: String)

  @Query("DELETE FROM cached_sheet_rows WHERE workspaceId = :workspaceId")
  suspend fun clearWorkspace(workspaceId: String)
}
