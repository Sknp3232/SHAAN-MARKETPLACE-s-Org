package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SearchFilters
import com.example.model.SheetRecord
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HighlightedText
import com.example.ui.components.SearchBarHero
import com.example.ui.components.StatusPill
import com.example.ui.components.SyncProgressStrip
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
  viewModel: MainWorkspaceViewModel,
  initialQuery: String = "",
  filterSheetId: String? = null,
  onOpenRecordDetail: (String) -> Unit,
  onConnectGoogleAuthClick: () -> Unit,
) {
  val context = LocalContext.current
  val extended = LocalSmartSheetColors.current

  val query by viewModel.searchQuery.collectAsStateWithLifecycle()
  val filters by viewModel.searchFilters.collectAsStateWithLifecycle()
  val results by viewModel.searchResults.collectAsStateWithLifecycle()
  val suggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
  val savedSearches by viewModel.savedSearchesState.collectAsStateWithLifecycle()
  val limit by viewModel.searchResultsLimit.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val worksheetsUi by viewModel.worksheetsState.collectAsStateWithLifecycle()
  val employeesUi by viewModel.employeesState.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val syncLabel by viewModel.syncProgressLabel.collectAsStateWithLifecycle()
  val isGoogleAuthorized by viewModel.isGoogleAuthorized.collectAsStateWithLifecycle()
  val settings by viewModel.settingsState.collectAsStateWithLifecycle()
  val cachedRows by viewModel.cachedRows.collectAsStateWithLifecycle()

  var showFilters by remember { mutableStateOf(false) }
  var showScanner by remember { mutableStateOf(false) }

  LaunchedEffect(initialQuery, filterSheetId) {
    if (initialQuery.isNotBlank() && query != initialQuery) {
      viewModel.updateSearchQuery(initialQuery)
    }
    if (filterSheetId != null && filters.sheetConnectionId != filterSheetId) {
      viewModel.updateSearchFilters(filters.copy(sheetConnectionId = filterSheetId))
    }
  }

  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val worksheets = (worksheetsUi as? UiState.Success)?.data.orEmpty()
  val employees = (employeesUi as? UiState.Success)?.data.orEmpty()
  val visibleResults = remember(results, limit) { results.take(limit) }
  val lastSyncMillis = remember(cachedRows) { cachedRows.maxOfOrNull { it.syncedAtMillis } }

  if (showScanner) {
    ImeiScannerModal(
      title = "Scan IMEI to Search",
      checkDuplicate = { imei -> viewModel.checkDuplicateImei(imei) },
      onConfirm = { scanned ->
        showScanner = false
        viewModel.updateSearchQuery(scanned)
        viewModel.recordSearchHistory(saved = false)
      },
      onDismiss = { showScanner = false },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(text = "Global Live Search", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "${results.size} matching records across ${sheets.size} sheets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (viewModel.canExportData() && results.isNotEmpty()) {
          OutlinedButton(
            onClick = {
              val csv = SearchEngine.recordsToCsv(results, settings.maskAadhaarByDefault)
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/csv"
                  putExtra(Intent.EXTRA_SUBJECT, "Smart Sheet Search Export (${results.size} records)")
                  putExtra(Intent.EXTRA_TEXT, csv)
                }
              context.startActivity(Intent.createChooser(shareIntent, "Export Search Results CSV"))
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("export_search_csv_button"),
          ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("CSV")
          }
        }
      }
    }

    item {
      SearchBarHero(
        query = query,
        onQueryChange = { viewModel.updateSearchQuery(it) },
        onSearchSubmit = { viewModel.recordSearchHistory(saved = false) },
        onScanBarcodeClick = { showScanner = true },
      )
    }

    // Autocomplete Suggestions while typing
    if (suggestions.isNotEmpty() && query.length >= 2) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          suggestions.forEach { suggestion ->
            AssistChip(
              onClick = {
                viewModel.updateSearchQuery(suggestion)
                viewModel.recordSearchHistory(saved = false)
              },
              label = { Text(suggestion, maxLines = 1) },
            )
          }
        }
      }
    }

    // Recent & Saved Search Chips when query is blank
    if (query.isBlank() && savedSearches.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Recent & Saved Searches",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            savedSearches.take(10).forEach { s ->
              FilterChip(
                selected = s.saved,
                onClick = { viewModel.updateSearchQuery(s.query) },
                label = { Text(if (s.saved) "★ ${s.query}" else s.query, maxLines = 1) },
              )
            }
          }
        }
      }
    }

    // Toolbar: Filters, Sort, Save Search, Clear Filters
    item {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilterChip(
          selected = showFilters || filters.isAnyActive(),
          onClick = { showFilters = !showFilters },
          leadingIcon = {
            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          label = { Text(if (filters.isAnyActive()) "Filters Active" else "Filters") },
          modifier = Modifier.testTag("toggle_search_filters"),
        )
        FilterChip(
          selected = filters.sortAscending,
          onClick = {
            viewModel.updateSearchFilters(filters.copy(sortAscending = !filters.sortAscending))
          },
          leadingIcon = {
            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
          },
          label = { Text(if (filters.sortAscending) "Sort: Row Asc" else "Sort: Latest") },
        )
        if (query.isNotBlank()) {
          AssistChip(
            onClick = { viewModel.recordSearchHistory(saved = true) },
            leadingIcon = {
              Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text("Save Search") },
          )
        }
        if (filters.isAnyActive()) {
          AssistChip(
            onClick = { viewModel.clearSearchFilters() },
            leadingIcon = {
              Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = { Text("Clear All") },
          )
        }
      }
    }

    // Expandable Advanced Multi-Column Filter Panel
    item {
      AnimatedVisibility(visible = showFilters) {
        AdvancedFiltersPanel(
          filters = filters,
          sheets = sheets,
          worksheets = worksheets,
          employees = employees,
          onFiltersChange = { viewModel.updateSearchFilters(it) },
          onClearAll = { viewModel.clearSearchFilters() },
        )
      }
    }

    item {
      SyncProgressStrip(
        isSyncing = isSyncing,
        label = syncLabel,
        lastSyncLabel = SearchEngine.formatRelativeTime(lastSyncMillis),
        onSyncClick = {
          if (viewModel.isAdmin && !isGoogleAuthorized) onConnectGoogleAuthClick()
          else viewModel.syncAllActiveSheets()
        },
      )
    }

    if (visibleResults.isEmpty()) {
      item {
        EmptyStateCard(
          title = if (cachedRows.isEmpty()) "No Synchronized Records Yet" else "No Matching Records",
          message =
            if (cachedRows.isEmpty()) {
              "Connect and synchronize a Google Spreadsheet to index records for instant live search."
            } else {
              "Try removing some filters or searching by partial mobile, IMEI, customer name, or invoice."
            },
        )
      }
    } else {
      items(visibleResults, key = { it.id }, contentType = { "search_result" }) { record ->
        SearchResultCard(
          record = record,
          query = query,
          maskAadhaar = settings.maskAadhaarByDefault,
          revealAadhaar = viewModel.canViewFullAadhaar() && !settings.maskAadhaarByDefault,
          onClick = {
            viewModel.selectRecord(record.id)
            onOpenRecordDetail(record.id)
          },
        )
      }

      if (results.size > visibleResults.size) {
        item {
          OutlinedButton(
            onClick = { viewModel.loadMoreSearchResults() },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
          ) {
            Text("Load More (${results.size - visibleResults.size} remaining)")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFiltersPanel(
  filters: SearchFilters,
  sheets: List<com.example.model.SheetConnection>,
  worksheets: List<com.example.model.WorksheetMetadata>,
  employees: List<com.example.model.EmployeeRecord>,
  onFiltersChange: (SearchFilters) -> Unit,
  onClearAll: () -> Unit,
) {
  var sheetMenuExpanded by remember { mutableStateOf(false) }
  var wsMenuExpanded by remember { mutableStateOf(false) }
  var empMenuExpanded by remember { mutableStateOf(false) }
  var colMenuExpanded by remember { mutableStateOf(false) }

  val selectedSheet = sheets.firstOrNull { it.id == filters.sheetConnectionId }
  val availableWorksheets =
    worksheets.filter { filters.sheetConnectionId == null || it.sheetConnectionId == filters.sheetConnectionId }
  val availableHeaders = availableWorksheets.flatMap { it.headers }.distinct()

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Advanced Filters", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClearAll) { Text("Clear All Filters") }
      }

      // Spreadsheet Selector
      ExposedDropdownMenuBox(
        expanded = sheetMenuExpanded,
        onExpandedChange = { sheetMenuExpanded = it },
      ) {
        OutlinedTextField(
          value = selectedSheet?.customName ?: "All Connected Spreadsheets",
          onValueChange = {},
          readOnly = true,
          label = { Text("Spreadsheet") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sheetMenuExpanded) },
          modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = sheetMenuExpanded,
          onDismissRequest = { sheetMenuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text("All Connected Spreadsheets") },
            onClick = {
              onFiltersChange(filters.copy(sheetConnectionId = null, worksheetTitle = null))
              sheetMenuExpanded = false
            },
          )
          sheets.forEach { s ->
            DropdownMenuItem(
              text = { Text(s.customName) },
              onClick = {
                onFiltersChange(filters.copy(sheetConnectionId = s.id, worksheetTitle = null))
                sheetMenuExpanded = false
              },
            )
          }
        }
      }

      // Worksheet Selector
      ExposedDropdownMenuBox(
        expanded = wsMenuExpanded,
        onExpandedChange = { wsMenuExpanded = it },
      ) {
        OutlinedTextField(
          value = filters.worksheetTitle ?: "All Worksheets",
          onValueChange = {},
          readOnly = true,
          label = { Text("Worksheet") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wsMenuExpanded) },
          modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
          expanded = wsMenuExpanded,
          onDismissRequest = { wsMenuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text("All Worksheets") },
            onClick = {
              onFiltersChange(filters.copy(worksheetTitle = null))
              wsMenuExpanded = false
            },
          )
          availableWorksheets.map { it.worksheetTitle }.distinct().forEach { wsTitle ->
            DropdownMenuItem(
              text = { Text(wsTitle) },
              onClick = {
                onFiltersChange(filters.copy(worksheetTitle = wsTitle))
                wsMenuExpanded = false
              },
            )
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = filters.customerQuery,
          onValueChange = { onFiltersChange(filters.copy(customerQuery = it)) },
          label = { Text("Customer") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = filters.mobileQuery,
          onValueChange = { onFiltersChange(filters.copy(mobileQuery = it)) },
          label = { Text("Mobile") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = filters.imeiQuery,
          onValueChange = { onFiltersChange(filters.copy(imeiQuery = it)) },
          label = { Text("IMEI") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = filters.dateQuery,
          onValueChange = { onFiltersChange(filters.copy(dateQuery = it)) },
          label = { Text("Date") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = filters.retailerQuery,
          onValueChange = { onFiltersChange(filters.copy(retailerQuery = it)) },
          label = { Text("Retailer") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = filters.modelQuery,
          onValueChange = { onFiltersChange(filters.copy(modelQuery = it)) },
          label = { Text("Product Model") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      if (employees.isNotEmpty()) {
        ExposedDropdownMenuBox(
          expanded = empMenuExpanded,
          onExpandedChange = { empMenuExpanded = it },
        ) {
          val selectedEmp = employees.firstOrNull { it.employeeUid == filters.addedByUid }
          OutlinedTextField(
            value = selectedEmp?.name ?: "Any Employee / Source",
            onValueChange = {},
            readOnly = true,
            label = { Text("Added By Employee") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = empMenuExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(
            expanded = empMenuExpanded,
            onDismissRequest = { empMenuExpanded = false },
          ) {
            DropdownMenuItem(
              text = { Text("Any Employee / Source") },
              onClick = {
                onFiltersChange(filters.copy(addedByUid = null))
                empMenuExpanded = false
              },
            )
            employees.forEach { emp ->
              DropdownMenuItem(
                text = { Text("${emp.name} (${emp.email})") },
                onClick = {
                  onFiltersChange(filters.copy(addedByUid = emp.employeeUid))
                  empMenuExpanded = false
                },
              )
            }
          }
        }
      }

      if (availableHeaders.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ExposedDropdownMenuBox(
            expanded = colMenuExpanded,
            onExpandedChange = { colMenuExpanded = it },
            modifier = Modifier.weight(1f),
          ) {
            OutlinedTextField(
              value = filters.columnHeader ?: "Any Column",
              onValueChange = {},
              readOnly = true,
              label = { Text("Column") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colMenuExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = colMenuExpanded,
              onDismissRequest = { colMenuExpanded = false },
            ) {
              DropdownMenuItem(
                text = { Text("Any Column") },
                onClick = {
                  onFiltersChange(filters.copy(columnHeader = null, columnValueQuery = ""))
                  colMenuExpanded = false
                },
              )
              availableHeaders.forEach { h ->
                DropdownMenuItem(
                  text = { Text(h) },
                  onClick = {
                    onFiltersChange(filters.copy(columnHeader = h))
                    colMenuExpanded = false
                  },
                )
              }
            }
          }
          OutlinedTextField(
            value = filters.columnValueQuery,
            onValueChange = { onFiltersChange(filters.copy(columnValueQuery = it)) },
            label = { Text("Column Value") },
            singleLine = true,
            enabled = filters.columnHeader != null,
            modifier = Modifier.weight(1f),
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable {
          onFiltersChange(filters.copy(onlyAppAdded = !filters.onlyAppAdded))
        },
      ) {
        Checkbox(
          checked = filters.onlyAppAdded,
          onCheckedChange = { onFiltersChange(filters.copy(onlyAppAdded = it)) },
        )
        Text("Only show records added through Smart Sheet", style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
fun SearchResultCard(
  record: SheetRecord,
  query: String,
  maskAadhaar: Boolean,
  revealAadhaar: Boolean,
  onClick: () -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  Card(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("search_result_${record.rowNumber}"),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          HighlightedText(
            text =
              record.customerName.ifBlank {
                record.cells.firstOrNull().orEmpty().ifBlank { "Row #${record.rowNumber}" }
              },
            query = query,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
          )
          if (record.mobileNumber.isNotBlank()) {
            HighlightedText(
              text = record.mobileNumber,
              query = query,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          label = if (record.isFromCache) "CACHED" else "LIVE SYNCED",
          containerColor =
            if (record.isFromCache) extended.warning.copy(alpha = 0.14f)
            else extended.healthy.copy(alpha = 0.14f),
          contentColor = if (record.isFromCache) extended.warning else extended.healthy,
        )
      }

      if (record.imei1.isNotBlank() || record.imei2.isNotBlank()) {
        HighlightedText(
          text =
            listOfNotNull(
              record.imei1.takeIf { it.isNotBlank() }?.let { "IMEI 1: $it" },
              record.imei2.takeIf { it.isNotBlank() }?.let { "IMEI 2: $it" },
            ).joinToString("  |  "),
          query = query,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      val extraDetails =
        listOfNotNull(
          record.productModel.takeIf { it.isNotBlank() }?.let { "Model: $it" },
          record.financeInfo.takeIf { it.isNotBlank() }?.let { "Finance: $it" },
          record.invoiceNumber.takeIf { it.isNotBlank() }?.let { "Inv: $it" },
          record.aadhaarNumber.takeIf { it.isNotBlank() }?.let {
            "Aadhaar: ${record.displayAadhaar(maskAadhaar, revealAadhaar)}"
          },
          record.recordDate.takeIf { it.isNotBlank() }?.let { "Date: $it" },
        )
      if (extraDetails.isNotEmpty()) {
        HighlightedText(
          text = extraDetails.joinToString("  •  "),
          query = query,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
      }

      if (record.matchedColumns.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          record.matchedColumns.take(4).forEach { col ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = extended.matchHighlight.copy(alpha = 0.45f),
            ) {
              Text(
                text = "Matched: $col",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${record.spreadsheetName} • ${record.worksheetTitle} • Row #${record.rowNumber}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = SearchEngine.formatRelativeTime(record.syncedAtMillis),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
