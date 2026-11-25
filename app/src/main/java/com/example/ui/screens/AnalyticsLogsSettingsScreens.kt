package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AccountStatus
import com.example.model.ConnectionHealth
import com.example.model.UiState
import com.example.ui.components.AccountManagementSection
import com.example.ui.components.DeleteAccountConfirmDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.LegalImportantLinksSection
import com.example.ui.components.StatusPill
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableIntStateOf
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.AnalyticsCategoryDimension
import com.example.util.AnalyticsSummary
import com.example.util.AppLinksConfig
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
  viewModel: MainWorkspaceViewModel,
  onNavigateToSheets: () -> Unit,
  onNavigateToEmployees: () -> Unit,
  onNavigateToSyncDiagnostics: () -> Unit,
  onNavigateToActivityLogs: () -> Unit,
  onNavigateToRecordAudits: () -> Unit,
  onBack: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val extended = LocalSmartSheetColors.current
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val worksheetsUi by viewModel.worksheetsState.collectAsStateWithLifecycle()
  val employeesUi by viewModel.employeesState.collectAsStateWithLifecycle()
  val auditsUi by viewModel.recordAuditsState.collectAsStateWithLifecycle()
  val logsUi by viewModel.activityLogsState.collectAsStateWithLifecycle()
  val syncLogsUi by viewModel.syncLogsState.collectAsStateWithLifecycle()
  val cachedRows by viewModel.cachedRows.collectAsStateWithLifecycle()
  val analyticsSummary by viewModel.analyticsSummary.collectAsStateWithLifecycle()
  val windowDays by viewModel.analyticsWindowDays.collectAsStateWithLifecycle()

  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val worksheets = (worksheetsUi as? UiState.Success)?.data.orEmpty()
  val employees = (employeesUi as? UiState.Success)?.data.orEmpty()
  val audits = (auditsUi as? UiState.Success)?.data.orEmpty()
  val logs = (logsUi as? UiState.Success)?.data.orEmpty()
  val syncLogs = (syncLogsUi as? UiState.Success)?.data.orEmpty()

  var selectedEmployeeFilter by remember { mutableStateOf<String?>(null) }

  val filteredAudits =
    remember(audits, selectedEmployeeFilter) {
      if (selectedEmployeeFilter == null) audits
      else audits.filter { it.addedByUid == selectedEmployeeFilter }
    }
  val activeEmployees = remember(employees) { employees.count { it.resolvedStatus() == AccountStatus.ACTIVE } }
  val suspendedEmployees = remember(employees) { employees.count { it.resolvedStatus() == AccountStatus.SUSPENDED } }
  val failedSyncs = remember(syncLogs) { syncLogs.count { it.status == "FAILED" } }
  val totalSearches = remember(employees) { employees.sumOf { it.totalSearches } }
  val totalRecordsMetric = remember(cachedRows.size, sheets) {
    maxOf(cachedRows.size.toLong(), sheets.sumOf { it.totalRecords })
  }

  Scaffold(
    topBar = {
      if (onBack != null) {
        TopAppBar(
          title = { Text("Admin Analytics") },
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          },
        )
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (onBack == null) {
        item {
          Text("Admin Analytics & Reports", style = MaterialTheme.typography.headlineMedium)
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          KpiMetricCard(
            title = "Connected Sheets",
            value = sheets.size.toString(),
            subtitle = "${sheets.count { it.active }} active",
            icon = Icons.Default.Analytics,
            accentColor = MaterialTheme.colorScheme.primary,
            testTag = "analytics_sheets_card",
            onClick = onNavigateToSheets,
            modifier = Modifier.weight(1f),
          )
          KpiMetricCard(
            title = "Total Records",
            value = totalRecordsMetric.toString(),
            subtitle = "${worksheets.size} worksheets",
            icon = Icons.Default.CloudQueue,
            accentColor = extended.healthy,
            testTag = "analytics_records_card",
            onClick = onNavigateToSheets,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          KpiMetricCard(
            title = "Employees",
            value = employees.size.toString(),
            subtitle = "$activeEmployees active • $suspendedEmployees suspended",
            icon = Icons.Default.Group,
            accentColor = MaterialTheme.colorScheme.secondary,
            testTag = "analytics_employees_card",
            onClick = onNavigateToEmployees,
            modifier = Modifier.weight(1f),
          )
          KpiMetricCard(
            title = "Failed Syncs",
            value = failedSyncs.toString(),
            subtitle = "$totalSearches emp searches",
            icon = Icons.Default.Sync,
            accentColor = if (failedSyncs > 0) extended.error else extended.healthy,
            testTag = "analytics_sync_card",
            onClick = onNavigateToSyncDiagnostics,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Visual Trends: Daily Record Entry Counts + Top-Performing Categories
      item {
        DailyEntriesChartCard(
          summary = analyticsSummary,
          windowDays = windowDays,
          onWindowDaysChange = { viewModel.setAnalyticsWindowDays(it) },
        )
      }

      item {
        TopCategoriesChartCard(summary = analyticsSummary)
      }

      // Sheet Volume Chart (Compose Canvas)
      if (sheets.isNotEmpty()) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text("Records Distribution by Spreadsheet", style = MaterialTheme.typography.titleMedium)
              val chartData = sheets.take(6).map { it.customName to maxOf(it.totalRecords, 1L) }
              val maxVal = chartData.maxOf { it.second }.coerceAtLeast(1L).toFloat()
              val barColor = MaterialTheme.colorScheme.primary
              val trackColor = MaterialTheme.colorScheme.surfaceVariant
              Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val barCount = chartData.size
                val slotWidth = size.width / barCount
                val barWidth = slotWidth * 0.52f
                chartData.forEachIndexed { index, (_, count) ->
                  val left = index * slotWidth + (slotWidth - barWidth) / 2f
                  val barHeight = (count.toFloat() / maxVal) * (size.height - 12f)
                  drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 4f),
                    size = Size(barWidth, size.height - 8f),
                    cornerRadius = CornerRadius(10f, 10f),
                  )
                  drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - 4f - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(10f, 10f),
                  )
                }
              }
              chartData.forEach { (name, count) ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                  Text("$count rows", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }

      // Employee Filter Chips
      if (employees.isNotEmpty()) {
        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { selectedEmployeeFilter = null }) {
              Text("All Users")
            }
            employees.take(3).forEach { emp ->
              OutlinedButton(onClick = { selectedEmployeeFilter = emp.employeeUid }) {
                Text(emp.name.substringBefore(" "), maxLines = 1)
              }
            }
          }
        }
      }

      // Export Analytics CSV
      item {
        Button(
          onClick = {
            val csv = buildString {
              appendLine("Category,Name,Value,Detail")
              sheets.forEach { s ->
                appendLine("\"Sheet\",\"${s.customName}\",\"${s.totalRecords}\",\"${s.connectionHealth}\"")
              }
              employees.forEach { e ->
                appendLine("\"Employee\",\"${e.name}\",\"${e.totalRecordsAdded}\",\"Searches: ${e.totalSearches}\"")
              }
              filteredAudits.forEach { a ->
                appendLine("\"RecordAdded\",\"${a.addedByName}\",\"Row ${a.rowNumber}\",\"${a.spreadsheetName}/${a.worksheetTitle}\"")
              }
            }
            val intent =
              Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Smart Sheet Analytics Report")
                putExtra(Intent.EXTRA_TEXT, csv)
              }
            context.startActivity(Intent.createChooser(intent, "Export Analytics CSV"))
          },
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("export_analytics_csv_button"),
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Export Complete Analytics Report (CSV)")
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Latest Records Added (${filteredAudits.size})", style = MaterialTheme.typography.titleMedium)
          OutlinedButton(onClick = onNavigateToRecordAudits) { Text("All Audits") }
        }
      }
      items(filteredAudits.take(6), key = { it.id }) { audit ->
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${audit.spreadsheetName} / ${audit.worksheetTitle} • Row #${audit.rowNumber}", style = MaterialTheme.typography.titleSmall)
            Text("Added by ${audit.addedByName} • ${audit.recordSummary}", style = MaterialTheme.typography.bodySmall)
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Latest Activities (${logs.size})", style = MaterialTheme.typography.titleMedium)
          OutlinedButton(onClick = onNavigateToActivityLogs) { Text("View Logs") }
        }
      }
      items(logs.take(5), key = { it.id }) { log ->
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${log.userName} • ${log.activityType.replace("_", " ")}", style = MaterialTheme.typography.titleSmall)
            Text(log.description, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogsScreen(
  viewModel: MainWorkspaceViewModel,
  onBack: () -> Unit,
) {
  val logsUi by viewModel.activityLogsState.collectAsStateWithLifecycle()
  val logs = (logsUi as? UiState.Success)?.data.orEmpty()
  var query by remember { mutableStateOf("") }
  var visibleLimit by remember(query) { mutableIntStateOf(40) }

  val filtered =
    remember(logs, query) {
      val q = query.trim().lowercase()
      if (q.isEmpty()) logs
      else logs.filter {
        it.description.lowercase().contains(q) ||
          it.userName.lowercase().contains(q) ||
          it.activityType.lowercase().contains(q) ||
          (it.sheetName?.lowercase()?.contains(q) == true)
      }
    }
  val visibleLogs = remember(filtered, visibleLimit) { filtered.take(visibleLimit) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Activity Audit Logs") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          label = { Text("Filter activity logs by user, action, or sheet") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      if (visibleLogs.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Activity Recorded",
            message = "Workspace actions, searches, syncs, and record insertions appear here automatically.",
            showIllustration = false,
          )
        }
      } else {
        items(visibleLogs, key = { it.id }, contentType = { "activity_log" }) { log ->
          Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(log.activityType.replace("_", " "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(SearchEngine.formatTimestamp(log.createdAt?.toDate()?.time), style = MaterialTheme.typography.labelSmall)
              }
              Text(log.description, style = MaterialTheme.typography.bodyMedium)
              Text(
                "${log.userName} (${log.userEmail}) • Role: ${log.userRole}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        if (filtered.size > visibleLogs.size) {
          item {
            OutlinedButton(
              onClick = { visibleLimit += 40 },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
              Text("Load More (${filtered.size - visibleLogs.size} remaining)")
            }
          }
        }
      }
    }
  }
}

@Composable
fun AdminHubScreen(
  viewModel: MainWorkspaceViewModel,
  onNavigateToEmployees: () -> Unit,
  onNavigateToPermissionsMatrix: () -> Unit,
  onNavigateToAnalytics: () -> Unit,
  onNavigateToActivityLogs: () -> Unit,
  onNavigateToSyncDiagnostics: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToProfile: () -> Unit,
  onSignOut: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Admin Console", style = MaterialTheme.typography.headlineMedium)
        Text(
          "${viewModel.userProfile.displayName} • ${viewModel.userProfile.email}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    item { AdminMenuRow("Employees & Invitations", "Invite links, suspend/reactivate, activity stats", Icons.Default.Group, onNavigateToEmployees) }
    item { AdminMenuRow("Permissions Matrix", "Sheet-level access rules per employee", Icons.Default.Security, onNavigateToPermissionsMatrix) }
    item { AdminMenuRow("Admin Analytics & Reports", "Usage breakdown, charts, and CSV exports", Icons.Default.Analytics, onNavigateToAnalytics) }
    item { AdminMenuRow("Activity Audit Logs", "Track logins, searches, record adds, and edits", Icons.Default.History, onNavigateToActivityLogs) }
    item { AdminMenuRow("Sync Diagnostics & Errors", "View sheet sync health and API error logs", Icons.Default.Sync, onNavigateToSyncDiagnostics) }
    item { AdminMenuRow("Workspace Settings & Theme", "Auto-sync interval, Aadhaar masking, appearance", Icons.Default.Settings, onNavigateToSettings) }
    item { AdminMenuRow("My Account & Legal Links", "Profile info, Privacy Policy, Terms, Home Page & Deletion", Icons.Default.Person, onNavigateToProfile) }
    item {
      Spacer(Modifier.height(6.dp))
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("admin_sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Sign Out")
      }
    }
  }
}

@Composable
private fun AdminMenuRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSettingsScreen(
  viewModel: MainWorkspaceViewModel,
  darkThemeOverride: Boolean?,
  onSetDarkThemeOverride: (Boolean?) -> Unit,
  onSignOut: () -> Unit,
  onBack: () -> Unit,
) {
  val settings by viewModel.settingsState.collectAsStateWithLifecycle()
  var autoSyncText by remember(settings) { mutableStateOf(settings.autoSyncIntervalMinutes.toString()) }
  var maskAadhaar by remember(settings) { mutableStateOf(settings.maskAadhaarByDefault) }
  var allowEmployeeExport by remember(settings) { mutableStateOf(settings.allowEmployeeExport) }
  var duplicateImeiCheck by remember(settings) { mutableStateOf(settings.duplicateImeiCheckEnabled) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Workspace Settings") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Synchronization & Security", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
              value = autoSyncText,
              onValueChange = { autoSyncText = it.filter { c -> c.isDigit() } },
              label = { Text("Auto-Sync Interval (minutes)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            SettingToggleRow("Mask Aadhaar by default (XXXX-XXXX-1234)", maskAadhaar) { maskAadhaar = it }
            SettingToggleRow("Allow Employees to Export CSV", allowEmployeeExport) { allowEmployeeExport = it }
            SettingToggleRow("Duplicate 15-Digit IMEI Detection", duplicateImeiCheck) { duplicateImeiCheck = it }
            Button(
              onClick = {
                viewModel.saveWorkspaceSettings(
                  settings.copy(
                    autoSyncIntervalMinutes = autoSyncText.toLongOrNull()?.coerceIn(5L, 1440L) ?: 15L,
                    maskAadhaarByDefault = maskAadhaar,
                    allowEmployeeExport = allowEmployeeExport,
                    duplicateImeiCheckEnabled = duplicateImeiCheck,
                  )
                )
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("save_settings_button"),
            ) {
              Text("Save Workspace Settings")
            }
          }
        }
      }

      item {
        ThemeSelectorCard(
          darkThemeOverride = darkThemeOverride,
          onSelect = onSetDarkThemeOverride,
        )
      }

      item {
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(8.dp))
          Text("Sign Out")
        }
      }
    }
  }
}

@Composable
fun ProfileScreen(
  viewModel: MainWorkspaceViewModel,
  darkThemeOverride: Boolean?,
  onSetDarkThemeOverride: (Boolean?) -> Unit,
  isDeletingAccount: Boolean = false,
  onOpenLegalDocument: (LegalDocumentType) -> Unit = {},
  onDeleteAccount: () -> Unit = {},
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val extended = LocalSmartSheetColors.current
  val empRecord by viewModel.myEmployeeRecord.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val profile = viewModel.userProfile
  val links = remember(context) { AppLinksConfig.resolve(context) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  if (showDeleteDialog) {
    DeleteAccountConfirmDialog(
      isAdmin = viewModel.isAdmin,
      onConfirmDelete = {
        showDeleteDialog = false
        onDeleteAccount()
      },
      onDismiss = { showDeleteDialog = false },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Section 1: PROFILE
    item {
      Text(
        text = "PROFILE",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    item {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        EmployeeAvatar(name = profile.displayName, photoUrl = profile.photoUrl)
        Column(modifier = Modifier.weight(1f)) {
          Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
          Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(
          label = profile.resolvedStatus().name,
          containerColor = extended.healthy.copy(alpha = 0.14f),
          contentColor = extended.healthy,
        )
      }
    }

    // Required Section: MY ACCOUNT INFORMATION
    item {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("my_account_information_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "MY ACCOUNT INFORMATION",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          InfoLine("Account Role", profile.role)
          InfoLine("Account Status", (empRecord?.status ?: profile.status))
          InfoLine(
            "Added by",
            empRecord?.addedByAdminName ?: profile.addedByAdminName ?: "Self (Workspace Admin)",
          )
          InfoLine(
            "Admin Email",
            empRecord?.addedByAdminEmail ?: profile.addedByAdminEmail ?: profile.email,
          )
          InfoLine(
            "Added on",
            SearchEngine.formatTimestamp((empRecord?.invitedAt ?: profile.invitedAt ?: profile.createdAt)?.toDate()?.time),
          )
          InfoLine(
            "Joined on",
            SearchEngine.formatTimestamp((empRecord?.joinedAt ?: profile.joinedAt ?: profile.createdAt)?.toDate()?.time),
          )
          if (!viewModel.isAdmin) {
            InfoLine(
              "Default Permission",
              empRecord?.resolvedDefaultPermission()?.label ?: "Search Only",
            )
            Text("Assigned Sheets & Permissions:", style = MaterialTheme.typography.titleSmall)
            if (sheets.isEmpty()) {
              Text(
                "No sheets currently assigned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              sheets.forEach { sheet ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("• ${sheet.customName}", style = MaterialTheme.typography.bodySmall)
                  Text(
                    viewModel.permissionForSheet(sheet.id).label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }
    }

    item {
      ThemeSelectorCard(
        darkThemeOverride = darkThemeOverride,
        onSelect = onSetDarkThemeOverride,
      )
    }

    // Section 2: LEGAL & IMPORTANT LINKS
    item {
      LegalImportantLinksSection(
        links = links,
        onViewDocument = onOpenLegalDocument,
        onShareLink = { title, url ->
          val shared = AppLinksConfig.shareLink(context, title, url)
          if (!shared) {
            viewModel.emitToast("Link copied successfully")
          }
        },
        onCopyLink = { label, url ->
          if (AppLinksConfig.copyLinkToClipboard(context, label, url)) {
            viewModel.emitToast("Link copied successfully")
          } else {
            viewModel.emitToast("Unable to copy link")
          }
        },
      )
    }

    // Section 3: ACCOUNT MANAGEMENT
    item {
      AccountManagementSection(
        isDeletingAccount = isDeletingAccount,
        accountDeletionUrl = links.accountDeletionUrl,
        onSignOut = onSignOut,
        onRequestDeleteAccount = { showDeleteDialog = true },
        onOpenDeletionGuide = {
          AppLinksConfig.openExternalUrl(context, links.accountDeletionUrl)
        },
      )
    }
  }
}

// --- CLICKABLE DASHBOARD CARD DETAIL SCREENS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksheetsDetailScreen(
  viewModel: MainWorkspaceViewModel,
  onOpenWorksheetInSearch: (String, String) -> Unit,
  onBack: () -> Unit,
) {
  val worksheetsUi by viewModel.worksheetsState.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val worksheets = (worksheetsUi as? UiState.Success)?.data.orEmpty()
  val sheetsById = (sheetsUi as? UiState.Success)?.data.orEmpty().associateBy { it.id }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Worksheets (${worksheets.size})") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (worksheets.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Worksheets Indexed",
            message = "Connect a spreadsheet and synchronize its tabs to inspect column headers and row counts here.",
            showIllustration = false,
          )
        }
      } else {
        items(worksheets, key = { it.id }) { ws ->
          val parentName = sheetsById[ws.sheetConnectionId]?.customName ?: "Spreadsheet"
          Card(
            modifier =
              Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onOpenWorksheetInSearch(ws.sheetConnectionId, ws.worksheetTitle) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("$parentName / ${ws.worksheetTitle}", style = MaterialTheme.typography.titleMedium)
              Text("${ws.recordCount} rows • ${ws.headers.size} columns", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
              if (ws.headers.isNotEmpty()) {
                Text("Headers: ${ws.headers.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsBreakdownScreen(
  viewModel: MainWorkspaceViewModel,
  onOpenSheetInSearch: (String) -> Unit,
  onBack: () -> Unit,
) {
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val cachedRows by viewModel.cachedRows.collectAsStateWithLifecycle()
  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val countsBySheet = remember(cachedRows) { cachedRows.groupingBy { it.sheetConnectionId }.eachCount() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Records Breakdown (${cachedRows.size})") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(sheets, key = { it.id }) { sheet ->
        val cached = countsBySheet[sheet.id] ?: 0
        Card(
          modifier = Modifier.fillMaxWidth().clickable { onOpenSheetInSearch(sheet.id) },
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(sheet.customName, style = MaterialTheme.typography.titleMedium)
              Text(
                "$cached locally indexed • ${sheet.selectedWorksheets.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text("Search →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDiagnosticsScreen(
  viewModel: MainWorkspaceViewModel,
  onConnectGoogleAuthClick: () -> Unit,
  onBack: () -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val syncLogsUi by viewModel.syncLogsState.collectAsStateWithLifecycle()
  val isGoogleAuthorized by viewModel.isGoogleAuthorized.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val syncLogs = (syncLogsUi as? UiState.Success)?.data.orEmpty()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Sync Status & API Errors") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        val requiresOAuth = viewModel.isAdmin && !isGoogleAuthorized
        Button(
          onClick = {
            if (requiresOAuth) onConnectGoogleAuthClick() else viewModel.syncAllActiveSheets()
          },
          enabled = !isSyncing,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text(
            when {
              requiresOAuth -> "Authorize Google OAuth First"
              !viewModel.isAdmin -> "Refresh Assigned Workspace Sheets"
              else -> "Retry Sync All Active Sheets"
            }
          )
        }
      }
      item { Text("Connection Health", style = MaterialTheme.typography.titleMedium) }
      items(sheets, key = { it.id }) { sheet ->
        val health = sheet.resolvedHealth()
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(sheet.customName, style = MaterialTheme.typography.titleSmall)
              StatusPill(
                label = health.name,
                containerColor = if (health == ConnectionHealth.ERROR) extended.error.copy(alpha = 0.14f) else extended.healthy.copy(alpha = 0.14f),
                contentColor = if (health == ConnectionHealth.ERROR) extended.error else extended.healthy,
              )
            }
            Text("Last Sync: ${SearchEngine.formatTimestamp(sheet.lastSyncAt?.toDate()?.time)}", style = MaterialTheme.typography.bodySmall)
            if (!sheet.lastSyncError.isNullOrBlank()) {
              Text("API Error: ${sheet.lastSyncError}", style = MaterialTheme.typography.bodySmall, color = extended.error)
            }
          }
        }
      }
      item { Text("Synchronization History (${syncLogs.size})", style = MaterialTheme.typography.titleMedium) }
      items(syncLogs, key = { it.id }) { log ->
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("${log.sheetName} (${log.status})", style = MaterialTheme.typography.titleSmall)
              Text("${log.recordsSynced} rows • ${log.durationMs}ms", style = MaterialTheme.typography.labelSmall)
            }
            if (!log.errorMessage.isNullOrBlank()) {
              Text(log.errorMessage, style = MaterialTheme.typography.bodySmall, color = extended.error)
            }
            Text(
              "By ${log.triggeredByName} • ${SearchEngine.formatTimestamp(log.createdAt?.toDate()?.time)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistoryDetailScreen(
  viewModel: MainWorkspaceViewModel,
  onRunSearch: (String) -> Unit,
  onBack: () -> Unit,
) {
  val searches by viewModel.savedSearchesState.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Search History & Saved") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (searches.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Saved or Recent Searches",
            message = "Run a search and press Enter or tap Save Search to bookmark frequent queries.",
            showIllustration = false,
          )
        }
      } else {
        items(searches, key = { it.id }) { s ->
          Card(
            modifier = Modifier.fillMaxWidth().clickable { onRunSearch(s.query) },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(s.query, style = MaterialTheme.typography.titleMedium)
                Text(
                  "${if (s.saved) "Saved • " else "Recent • "}${s.resultCount} matches • ${s.filtersSummary}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { viewModel.deleteSavedSearch(s.id) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete search")
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordAuditsScreen(
  viewModel: MainWorkspaceViewModel,
  onBack: () -> Unit,
) {
  val auditsUi by viewModel.recordAuditsState.collectAsStateWithLifecycle()
  val audits = (auditsUi as? UiState.Success)?.data.orEmpty()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("App-Added Records (${audits.size})") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (audits.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Records Added Yet",
            message = "Records inserted or edited through Smart Sheet Manager appear here with full user and timestamp metadata.",
            showIllustration = false,
          )
        }
      } else {
        items(audits, key = { it.id }) { audit ->
          Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("${audit.spreadsheetName} / ${audit.worksheetTitle}", style = MaterialTheme.typography.titleSmall)
                StatusPill(
                  label = "${audit.actionType} • Row #${audit.rowNumber}",
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.primary,
                )
              }
              Text(audit.recordSummary, style = MaterialTheme.typography.bodyMedium)
              Text(
                "By ${audit.addedByName} (${audit.addedByEmail}) • ${SearchEngine.formatTimestamp(audit.createdAt?.toDate()?.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InfoLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall)
  }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun ThemeSelectorCard(
  darkThemeOverride: Boolean?,
  onSelect: (Boolean?) -> Unit,
) {
  Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeOptionChip("System", darkThemeOverride == null, { onSelect(null) }, Modifier.weight(1f))
        ThemeOptionChip("Light", darkThemeOverride == false, { onSelect(false) }, Modifier.weight(1f))
        ThemeOptionChip("Dark", darkThemeOverride == true, { onSelect(true) }, Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun ThemeOptionChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (selected) {
    Button(onClick = onClick, modifier = modifier.minimumInteractiveComponentSize()) {
      Text(label, maxLines = 1)
    }
  } else {
    OutlinedButton(onClick = onClick, modifier = modifier.minimumInteractiveComponentSize()) {
      Text(label, maxLines = 1)
    }
  }
}

@Composable
fun DailyEntriesChartCard(
  summary: AnalyticsSummary,
  windowDays: Int,
  onWindowDaysChange: (Int) -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val gridColor = MaterialTheme.colorScheme.outlineVariant

  Card(
    modifier = Modifier.fillMaxWidth().testTag("analytics_daily_entries_chart"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Daily Record Entry Counts", style = MaterialTheme.typography.titleMedium)
          Text(
            "Last $windowDays days trend",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = windowDays == 7,
            onClick = { onWindowDaysChange(7) },
            label = { Text("7D") },
            modifier = Modifier.testTag("analytics_window_7d"),
          )
          FilterChip(
            selected = windowDays == 14,
            onClick = { onWindowDaysChange(14) },
            label = { Text("14D") },
            modifier = Modifier.testTag("analytics_window_14d"),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusPill(
          label = "Total: ${summary.totalEntriesInWindow}",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = primaryColor,
        )
        StatusPill(
          label = "Peak: ${summary.peakDayLabel} (${summary.peakDayCount})",
          containerColor = extended.healthy.copy(alpha = 0.14f),
          contentColor = extended.healthy,
        )
        StatusPill(
          label = "Today: ${summary.todayCount}",
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = secondaryColor,
        )
      }

      val points = summary.dailyEntries
      if (points.isEmpty() || summary.totalEntriesInWindow == 0) {
        Text(
          text = "No dated record entries in the last $windowDays days yet. Sync or add records to visualize daily activity.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val maxCount = remember(points) { points.maxOf { it.count }.coerceAtLeast(1) }
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
          val chartBottom = size.height - 8f
          val chartTop = 8f
          val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

          // Horizontal reference gridlines (0%, 50%, 100%)
          listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = chartBottom - (chartHeight * fraction)
            drawLine(
              color = gridColor,
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1.5f,
            )
          }

          val count = points.size.coerceAtLeast(1)
          val slotWidth = size.width / count
          val barWidth = (slotWidth * 0.54f).coerceIn(6f, 44f)
          points.forEachIndexed { idx, pt ->
            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
              color = trackColor,
              topLeft = Offset(left, chartTop),
              size = Size(barWidth, chartHeight),
              cornerRadius = CornerRadius(8f, 8f),
            )
            if (pt.count > 0) {
              val h = ((pt.count.toFloat() / maxCount.toFloat()) * chartHeight).coerceAtLeast(6f)
              val isPeak = pt.count == summary.peakDayCount
              drawRoundRect(
                color = if (isPeak) secondaryColor else primaryColor,
                topLeft = Offset(left, chartBottom - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(8f, 8f),
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          points.forEach { pt ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = pt.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (pt.count > 0) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = pt.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TopCategoriesChartCard(
  summary: AnalyticsSummary,
) {
  var selectedDimension by remember { mutableStateOf(AnalyticsCategoryDimension.MODELS) }
  val categories =
    remember(summary, selectedDimension) { summary.categoriesFor(selectedDimension) }
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val barPalette = remember(primaryColor, secondaryColor, tertiaryColor) {
    listOf(primaryColor, secondaryColor, tertiaryColor)
  }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("analytics_top_categories_chart"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Top-Performing Categories", style = MaterialTheme.typography.titleMedium)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AnalyticsCategoryDimension.entries.forEach { dim ->
          FilterChip(
            selected = selectedDimension == dim,
            onClick = { selectedDimension = dim },
            label = { Text(dim.label) },
            modifier = Modifier.testTag("analytics_cat_${dim.name.lowercase()}"),
          )
        }
      }

      if (categories.isEmpty()) {
        Text(
          text = "No ${selectedDimension.label.lowercase()} data available yet in synchronized spreadsheets.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        val maxCount = remember(categories) { categories.maxOf { it.count }.coerceAtLeast(1) }
        categories.forEachIndexed { idx, item ->
          val barColor = barPalette[idx % barPalette.size]
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${idx + 1}. ${item.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Text(
                text = "${item.count} (${item.sharePercent}%)",
                style = MaterialTheme.typography.labelMedium,
                color = barColor,
              )
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
              drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(6f, 6f),
              )
              val fillWidth =
                ((item.count.toFloat() / maxCount.toFloat()) * size.width).coerceAtLeast(8f)
              drawRoundRect(
                color = barColor,
                topLeft = Offset.Zero,
                size = Size(fillWidth, size.height),
                cornerRadius = CornerRadius(6f, 6f),
              )
            }
          }
        }
      }
    }
  }
}

