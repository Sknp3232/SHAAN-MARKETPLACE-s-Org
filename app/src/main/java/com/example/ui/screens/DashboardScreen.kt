package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AccountStatus
import com.example.model.ConnectionHealth
import com.example.model.SheetRecord
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HighlightedText
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SearchBarHero
import com.example.ui.components.StatusPill
import com.example.ui.components.SyncProgressStrip
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel

@Composable
fun DashboardScreen(
  viewModel: MainWorkspaceViewModel,
  onNavigateToSearch: (String) -> Unit,
  onNavigateToRecordDetail: (String) -> Unit,
  onNavigateToAddRecord: () -> Unit,
  onNavigateToSheets: () -> Unit,
  onNavigateToWorksheetsDetail: () -> Unit,
  onNavigateToRecordsBreakdown: () -> Unit,
  onNavigateToEmployees: () -> Unit,
  onNavigateToSearchHistory: () -> Unit,
  onNavigateToRecordAudits: () -> Unit,
  onNavigateToSyncDiagnostics: () -> Unit,
  onNavigateToActivityLogs: () -> Unit,
  onNavigateToAnalytics: () -> Unit,
  onConnectGoogleAuthClick: () -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val worksheetsUi by viewModel.worksheetsState.collectAsStateWithLifecycle()
  val employeesUi by viewModel.employeesState.collectAsStateWithLifecycle()
  val logsUi by viewModel.activityLogsState.collectAsStateWithLifecycle()
  val auditsUi by viewModel.recordAuditsState.collectAsStateWithLifecycle()
  val syncLogsUi by viewModel.syncLogsState.collectAsStateWithLifecycle()
  val cachedRows by viewModel.cachedRows.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val savedSearches by viewModel.savedSearchesState.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val syncLabel by viewModel.syncProgressLabel.collectAsStateWithLifecycle()
  val isGoogleAuthorized by viewModel.isGoogleAuthorized.collectAsStateWithLifecycle()
  val connectedGoogleEmail by viewModel.connectedGoogleEmail.collectAsStateWithLifecycle()
  val googleAuthError by viewModel.googleAuthError.collectAsStateWithLifecycle()
  val settings by viewModel.settingsState.collectAsStateWithLifecycle()

  var showScanner by remember { mutableStateOf(false) }

  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val worksheets = (worksheetsUi as? UiState.Success)?.data.orEmpty()
  val employees = (employeesUi as? UiState.Success)?.data.orEmpty()
  val logs = (logsUi as? UiState.Success)?.data.orEmpty()
  val audits = (auditsUi as? UiState.Success)?.data.orEmpty()
  val syncLogs = (syncLogsUi as? UiState.Success)?.data.orEmpty()

  val totalRecords =
    remember(cachedRows.size, sheets) {
      maxOf(cachedRows.size.toLong(), sheets.sumOf { it.totalRecords })
    }
  val activeEmployees =
    remember(employees) { employees.count { it.resolvedStatus() == AccountStatus.ACTIVE } }
  val failedSheets =
    remember(sheets) { sheets.count { it.resolvedHealth() == ConnectionHealth.ERROR } }
  val failedSyncCount = remember(syncLogs) { syncLogs.count { it.status == "FAILED" } }
  val lastSyncMillis =
    remember(cachedRows, sheets) {
      cachedRows.maxOfOrNull { it.syncedAtMillis }
        ?: sheets.mapNotNull { it.lastSyncAt?.toDate()?.time }.maxOrNull()
    }
  val canAddAny =
    remember(sheets, viewModel.isAdmin) {
      sheets.any { it.active && viewModel.canAddToSheet(it.id) }
    }

  if (showScanner) {
    ImeiScannerModal(
      title = "Scan IMEI to Search",
      checkDuplicate = { imei -> viewModel.checkDuplicateImei(imei) },
      onConfirm = { imei ->
        showScanner = false
        viewModel.updateSearchQuery(imei)
        onNavigateToSearch(imei)
      },
      onDismiss = { showScanner = false },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              if (viewModel.isAdmin) "Admin Control Center"
              else "Welcome, ${viewModel.userProfile.displayName}",
            style = MaterialTheme.typography.headlineMedium,
          )
          Text(
            text =
              if (viewModel.isAdmin) viewModel.userProfile.email
              else "Assigned by ${viewModel.userProfile.addedByAdminName ?: "Workspace Admin"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          label = if (viewModel.isAdmin) "ADMIN" else "EMPLOYEE",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.primary,
        )
      }
    }

    // Prominent Global Search Bar
    item {
      SearchBarHero(
        query = searchQuery,
        onQueryChange = { viewModel.updateSearchQuery(it) },
        onSearchSubmit = {
          viewModel.recordSearchHistory(saved = false)
          onNavigateToSearch(searchQuery)
        },
        onScanBarcodeClick = { showScanner = true },
      )
    }

    // Instant live preview while typing right on the Dashboard!
    if (searchQuery.isNotBlank()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Instant Matches (${searchResults.size})",
            style = MaterialTheme.typography.titleMedium,
          )
          TextButton(onClick = { onNavigateToSearch(searchQuery) }) {
            Text("Open Full Search →")
          }
        }
      }
      if (searchResults.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No matching records",
            message = "No indexed rows match \"$searchQuery\". Try syncing sheets or adjusting filters.",
            showIllustration = false,
          )
        }
      } else {
        items(searchResults.take(5), key = { it.id }, contentType = { "preview_record" }) { record ->
          DashboardSearchPreviewCard(
            record = record,
            query = searchQuery,
            maskAadhaar = settings.maskAadhaarByDefault,
            revealAadhaar = viewModel.canViewFullAadhaar() && !settings.maskAadhaarByDefault,
            onClick = {
              viewModel.selectRecord(record.id)
              onNavigateToRecordDetail(record.id)
            },
          )
        }
      }
    }

    // Sync Strip & Google Workspace OAuth status
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

    if (viewModel.isAdmin && !isGoogleAuthorized) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
          border =
            BorderStroke(
              1.dp,
              if (!googleAuthError.isNullOrBlank()) extended.error.copy(alpha = 0.6f)
              else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Google Workspace OAuth 2.0",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Authorize your own Google Drive & Sheets to sync or insert rows.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(Modifier.size(8.dp))
              Button(
                onClick = onConnectGoogleAuthClick,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("authorize_google_button"),
              ) {
                Text("Connect")
              }
            }
            if (!googleAuthError.isNullOrBlank()) {
              Text(
                text = googleAuthError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = extended.error,
              )
            }
          }
        }
      }
    } else if (!viewModel.isAdmin) {
      item {
        Text(
          text = "Synced via Admin Google Sheets connection (${sheets.size} assigned sheets)",
          style = MaterialTheme.typography.labelSmall,
          color = extended.healthy,
        )
      }
    } else if (!connectedGoogleEmail.isNullOrBlank()) {
      item {
        Text(
          text = "Google Drive linked to: $connectedGoogleEmail",
          style = MaterialTheme.typography.labelSmall,
          color = extended.healthy,
        )
      }
    }

    // Primary Action Buttons
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (viewModel.isAdmin) {
          Button(
            onClick = onNavigateToSheets,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("connect_new_sheet_cta"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Connect Sheet", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        if (viewModel.isAdmin || canAddAny) {
          FilledTonalButton(
            onClick = onNavigateToAddRecord,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("add_new_record_cta"),
          ) {
            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("+ Add Record", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        if (!viewModel.isAdmin) {
          OutlinedButton(
            onClick = { onNavigateToSearch("") },
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Global Search", maxLines = 1)
          }
        }
      }
    }

    // Every Dashboard KPI Card is Clickable!
    item {
      Text(
        text = "Workspace Overview",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    item {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 700.dp) 3 else 2
        val cards =
          buildList<@Composable () -> Unit> {
            add {
              KpiMetricCard(
                title = "Connected Sheets",
                value = sheets.size.toString(),
                subtitle = "${sheets.count { it.active }} active",
                icon = Icons.Default.TableChart,
                accentColor = MaterialTheme.colorScheme.primary,
                testTag = "card_connected_sheets",
                onClick = onNavigateToSheets,
              )
            }
            add {
              KpiMetricCard(
                title = "Total Worksheets",
                value = worksheets.size.toString(),
                subtitle = "Tabs indexed",
                icon = Icons.Default.ViewList,
                accentColor = MaterialTheme.colorScheme.tertiary,
                testTag = "card_total_worksheets",
                onClick = onNavigateToWorksheetsDetail,
              )
            }
            add {
              KpiMetricCard(
                title = "Total Records",
                value = totalRecords.toString(),
                subtitle = "Searchable rows",
                icon = Icons.Default.Storage,
                accentColor = extended.healthy,
                testTag = "card_total_records",
                onClick = onNavigateToRecordsBreakdown,
              )
            }
            if (viewModel.isAdmin) {
              add {
                KpiMetricCard(
                  title = "Total Employees",
                  value = employees.size.toString(),
                  subtitle = "$activeEmployees active",
                  icon = Icons.Default.Group,
                  accentColor = MaterialTheme.colorScheme.secondary,
                  testTag = "card_total_employees",
                  onClick = onNavigateToEmployees,
                )
              }
            }
            add {
              KpiMetricCard(
                title = if (viewModel.isAdmin) "Total Searches" else "My Searches",
                value =
                  if (viewModel.isAdmin) {
                    val empSum = employees.sumOf { it.totalSearches }
                    maxOf(empSum, savedSearches.size.toLong()).toString()
                  } else {
                    savedSearches.size.toString()
                  },
                subtitle = "History & saved",
                icon = Icons.Default.Search,
                accentColor = extended.info,
                testTag = "card_total_searches",
                onClick = onNavigateToSearchHistory,
              )
            }
            add {
              KpiMetricCard(
                title = "Records Added",
                value = audits.size.toString(),
                subtitle = "Platform entries",
                icon = Icons.Default.NoteAdd,
                accentColor = MaterialTheme.colorScheme.primary,
                testTag = "card_records_added",
                onClick = onNavigateToRecordAudits,
              )
            }
            add {
              KpiMetricCard(
                title = "Sync Status",
                value = if (failedSheets > 0) "$failedSheets Issues" else "Healthy",
                subtitle = "$failedSyncCount failed runs",
                icon = if (failedSheets > 0) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                accentColor = if (failedSheets > 0) extended.error else extended.healthy,
                testTag = "card_sync_status",
                onClick = onNavigateToSyncDiagnostics,
              )
            }
            add {
              KpiMetricCard(
                title = "Recent Activities",
                value = logs.size.toString(),
                subtitle = "Audit trail",
                icon = Icons.Default.History,
                accentColor = MaterialTheme.colorScheme.secondary,
                testTag = "card_recent_activities",
                onClick = onNavigateToActivityLogs,
              )
            }
            if (viewModel.isAdmin) {
              add {
                KpiMetricCard(
                  title = "Admin Analytics",
                  value = "Reports",
                  subtitle = "CSV export & metrics",
                  icon = Icons.Default.Analytics,
                  accentColor = MaterialTheme.colorScheme.tertiary,
                  testTag = "card_admin_analytics",
                  onClick = onNavigateToAnalytics,
                )
              }
            }
          }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          cards.chunked(columns).forEach { rowCards ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              rowCards.forEach { card ->
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                  card()
                }
              }
              repeat(columns - rowCards.size) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
      }
    }

    // Recent Activity Preview (Clickable!)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "Recent Activity", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onNavigateToActivityLogs) {
          Text("See All →")
        }
      }
    }

    if (logs.isEmpty()) {
      item {
        EmptyStateCard(
          title = if (viewModel.isAdmin) "Connect your first Google Sheet" else "Workspace Ready",
          message =
            if (viewModel.isAdmin) {
              "Tap Connect Sheet to link spreadsheets from Google Drive, configure worksheets, and start instant search."
            } else {
              "Use Global Search above to look up records across your assigned spreadsheets."
            },
          actionLabel = if (viewModel.isAdmin) "+ CONNECT NEW GOOGLE SHEET" else null,
          onAction = if (viewModel.isAdmin) onNavigateToSheets else null,
        )
      }
    } else {
      items(logs.take(5), key = { it.id }, contentType = { "activity_preview" }) { log ->
        Card(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .clickable(onClick = onNavigateToActivityLogs),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = log.activityType.replace("_", " "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = SearchEngine.formatRelativeTime(log.createdAt?.toDate()?.time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = log.description,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${log.userName} (${log.userRole})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
fun DashboardSearchPreviewCard(
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
        .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        HighlightedText(
          text = record.customerName.ifBlank { record.cells.firstOrNull().orEmpty().ifBlank { "Row #${record.rowNumber}" } },
          query = query,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
        )
        StatusPill(
          label = if (record.isFromCache) "CACHED" else "LIVE SYNCED",
          containerColor =
            if (record.isFromCache) extended.warning.copy(alpha = 0.14f)
            else extended.healthy.copy(alpha = 0.14f),
          contentColor = if (record.isFromCache) extended.warning else extended.healthy,
        )
      }
      if (record.mobileNumber.isNotBlank() || record.imei1.isNotBlank()) {
        HighlightedText(
          text =
            listOfNotNull(
              record.mobileNumber.takeIf { it.isNotBlank() }?.let { "Mob: $it" },
              record.imei1.takeIf { it.isNotBlank() }?.let { "IMEI1: $it" },
              record.imei2.takeIf { it.isNotBlank() }?.let { "IMEI2: $it" },
            ).joinToString("  •  "),
          query = query,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (record.aadhaarNumber.isNotBlank()) {
        Text(
          text = "Aadhaar: ${record.displayAadhaar(maskAadhaar, revealAadhaar)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = "${record.spreadsheetName} / ${record.worksheetTitle} • Row #${record.rowNumber}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
