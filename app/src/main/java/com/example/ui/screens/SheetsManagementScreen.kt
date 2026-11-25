package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ConnectionHealth
import com.example.model.FieldInputType
import com.example.model.RemoteSpreadsheetPreview
import com.example.model.SheetConnection
import com.example.model.UiState
import com.example.model.WorksheetMetadata
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GoogleSheetPickerModalSheet
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel

@Composable
fun SheetsManagementScreen(
  viewModel: MainWorkspaceViewModel,
  onConnectGoogleAuthClick: () -> Unit,
  onOpenSheetInSearch: (String) -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val extended = LocalSmartSheetColors.current
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val worksheetsUi by viewModel.worksheetsState.collectAsStateWithLifecycle()
  val isGoogleAuthorized by viewModel.isGoogleAuthorized.collectAsStateWithLifecycle()
  val connectedGoogleEmail by viewModel.connectedGoogleEmail.collectAsStateWithLifecycle()
  val googleAuthError by viewModel.googleAuthError.collectAsStateWithLifecycle()
  val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
  val isLoadingDrive by viewModel.isLoadingDrive.collectAsStateWithLifecycle()
  val sheetPreview by viewModel.sheetPreview.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val worksheets = (worksheetsUi as? UiState.Success)?.data.orEmpty()

  var showConnectDialog by remember { mutableStateOf(false) }
  var showCreateSheetDialog by remember { mutableStateOf(false) }
  var editingSheet by remember { mutableStateOf<SheetConnection?>(null) }
  var configuringWorksheet by remember { mutableStateOf<WorksheetMetadata?>(null) }
  var disconnectTarget by remember { mutableStateOf<SheetConnection?>(null) }

  if (showCreateSheetDialog) {
    CreateSpreadsheetDialog(
      isGoogleAuthorized = isGoogleAuthorized,
      isSyncing = isSyncing,
      onAuthorizeGoogle = onConnectGoogleAuthClick,
      onCreateConfirmed = { title, tabTitle, headers ->
        viewModel.createNewSpreadsheet(title, tabTitle, headers) {
          showCreateSheetDialog = false
        }
      },
      onDismiss = { showCreateSheetDialog = false },
    )
  }

  if (showConnectDialog) {
    GoogleSheetPickerModalSheet(
      isGoogleAuthorized = isGoogleAuthorized,
      connectedEmail = connectedGoogleEmail ?: viewModel.userProfile.email,
      authError = googleAuthError,
      isLoadingDrive = isLoadingDrive,
      driveFiles = driveFiles,
      connectedSpreadsheetIds = sheets.map { it.spreadsheetId }.toSet(),
      preview = sheetPreview,
      onAuthorizeGoogle = onConnectGoogleAuthClick,
      onSearchDrive = { q -> viewModel.loadDriveSpreadsheets(q) },
      onInspectIdOrUrl = { id -> viewModel.inspectSpreadsheet(id) },
      onClearPreview = { viewModel.clearSheetPreview() },
      onConnectConfirmed = { preview, customName, selectedTabs ->
        viewModel.connectSpreadsheet(preview, customName, selectedTabs) {
          showConnectDialog = false
        }
      },
      onCreateNewSheetClick = { showCreateSheetDialog = true },
      onDismiss = {
        viewModel.clearSheetPreview()
        showConnectDialog = false
      },
    )
  }

  editingSheet?.let { target ->
    EditSheetDialog(
      sheet = target,
      onSave = { customName, active, selectedTabs, maskAadhaar ->
        viewModel.updateSheetSettings(target, customName, active, selectedTabs, maskAadhaar)
        editingSheet = null
      },
      onDismiss = { editingSheet = null },
    )
  }

  configuringWorksheet?.let { ws ->
    WorksheetFormConfigDialog(
      worksheet = ws,
      onSave = { reqFields, fieldTypes, dropdownOpts ->
        viewModel.updateWorksheetValidationConfig(ws, reqFields, fieldTypes, dropdownOpts)
        configuringWorksheet = null
      },
      onDismiss = { configuringWorksheet = null },
    )
  }

  disconnectTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { disconnectTarget = null },
      title = { Text("Disconnect '${target.customName}'?") },
      text = {
        Text(
          "This disconnects the spreadsheet from Smart Sheet Manager and clears its local cache. Your original Google Sheet in Google Drive will NOT be deleted."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.disconnectSheet(target)
            disconnectTarget = null
          }
        ) {
          Text("Disconnect")
        }
      },
      dismissButton = {
        TextButton(onClick = { disconnectTarget = null }) { Text("Cancel") }
      },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Google Sheets Management", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Google Sheets remains the single source of truth. Connect unlimited spreadsheets.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Google Account OAuth Status & Disconnect / Reconnect Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (isGoogleAuthorized) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
              else MaterialTheme.colorScheme.surface
          ),
        border =
          BorderStroke(
            1.dp,
            if (!googleAuthError.isNullOrBlank()) extended.error.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant,
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
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = if (isGoogleAuthorized) extended.healthy else MaterialTheme.colorScheme.primary,
              )
              Column {
                Text(
                  text =
                    if (isGoogleAuthorized) "Google Drive & Sheets Connected"
                    else "Connect Your Google Drive & Sheets",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text =
                    if (isGoogleAuthorized) {
                      "Account: ${connectedGoogleEmail ?: viewModel.userProfile.email} • Scopes: drive.file, spreadsheets"
                    } else {
                      "Each user authorizes their own Google account via OAuth 2.0."
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            StatusPill(
              label = if (isGoogleAuthorized) "CONNECTED" else "NOT LINKED",
              containerColor =
                if (isGoogleAuthorized) extended.healthy.copy(alpha = 0.14f)
                else extended.warning.copy(alpha = 0.14f),
              contentColor = if (isGoogleAuthorized) extended.healthy else extended.warning,
            )
          }

          if (!googleAuthError.isNullOrBlank()) {
            Text(
              text = googleAuthError.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = extended.error,
              modifier = Modifier.testTag("google_oauth_error_banner"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (isGoogleAuthorized) {
              OutlinedButton(
                onClick = { activity?.let { viewModel.disconnectGoogleWorkspace(it) } },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("disconnect_google_oauth_button"),
              ) {
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Disconnect Google")
              }
              FilledTonalButton(
                onClick = onConnectGoogleAuthClick,
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("reconnect_google_oauth_button"),
              ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Reconnect")
              }
            } else {
              Button(
                onClick = onConnectGoogleAuthClick,
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("connect_google_oauth_button"),
              ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Connect Google OAuth")
              }
            }
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = {
            showConnectDialog = true
            if (isGoogleAuthorized && driveFiles.isEmpty()) {
              viewModel.loadDriveSpreadsheets()
            }
          },
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("connect_new_google_sheet_button"),
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text("Connect Sheet")
        }
        FilledTonalButton(
          onClick = { showCreateSheetDialog = true },
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("create_new_google_sheet_button"),
        ) {
          Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text("Create Sheet")
        }
        IconButton(
          onClick = { viewModel.syncAllActiveSheets() },
          enabled = !isSyncing,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_all_sheets_button"),
        ) {
          Icon(Icons.Default.Sync, contentDescription = "Sync all active sheets")
        }
      }
    }

    if (sheets.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Google Sheets Connected",
          message =
            "Authorize your Google Account, browse Google Drive spreadsheets or paste a Spreadsheet ID/URL, and choose which worksheets to index.",
          actionLabel = "+ CONNECT NEW GOOGLE SHEET",
          onAction = { showConnectDialog = true },
        )
      }
    } else {
      items(sheets, key = { it.id }) { sheet ->
        val sheetTabs = worksheets.filter { it.sheetConnectionId == sheet.id }
        val health = sheet.resolvedHealth()
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  Icons.Default.TableChart,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text(text = sheet.customName, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = "Source: ${sheet.originalTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              StatusPill(
                label =
                  if (!sheet.active) "INACTIVE"
                  else health.name,
                containerColor =
                  when {
                    !sheet.active -> MaterialTheme.colorScheme.surfaceVariant
                    health == ConnectionHealth.ERROR -> extended.error.copy(alpha = 0.14f)
                    health == ConnectionHealth.SYNCING -> extended.info.copy(alpha = 0.14f)
                    else -> extended.healthy.copy(alpha = 0.14f)
                  },
                contentColor =
                  when {
                    !sheet.active -> MaterialTheme.colorScheme.onSurfaceVariant
                    health == ConnectionHealth.ERROR -> extended.error
                    health == ConnectionHealth.SYNCING -> extended.info
                    else -> extended.healthy
                  },
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${sheet.totalRecords} records • ${sheet.selectedWorksheets.size} tabs",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Synced: ${SearchEngine.formatRelativeTime(sheet.lastSyncAt?.toDate()?.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (!sheet.lastSyncError.isNullOrBlank() && health == ConnectionHealth.ERROR) {
              Text(
                text = "Error: ${sheet.lastSyncError}",
                style = MaterialTheme.typography.bodySmall,
                color = extended.error,
              )
            }

            // Worksheet chips + Configure Form Rules button
            sheetTabs.forEach { ws ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "• ${ws.worksheetTitle} (${ws.recordCount} rows, ${ws.headers.size} cols)",
                    style = MaterialTheme.typography.bodySmall,
                  )
                  if (ws.headers.isNotEmpty()) {
                    Text(
                      text = ws.headers.take(5).joinToString(", ") + if (ws.headers.size > 5) "…" else "",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                TextButton(onClick = { configuringWorksheet = ws }) {
                  Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.size(4.dp))
                  Text("Form Rules")
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { onOpenSheetInSearch(sheet.id) },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
              ) {
                Text("Search")
              }
              OutlinedButton(
                onClick = {
                  if (!isGoogleAuthorized) onConnectGoogleAuthClick()
                  else viewModel.syncSheet(sheet)
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Refresh")
              }
              IconButton(
                onClick = { editingSheet = sheet },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Default.Settings, contentDescription = "Configure sheet")
              }
              IconButton(
                onClick = { disconnectTarget = sheet },
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Icon(
                  Icons.Default.DeleteOutline,
                  contentDescription = "Disconnect sheet",
                  tint = extended.error,
                )
              }
            }
          }
        }
      }
    }
  }
}


@Composable
private fun EditSheetDialog(
  sheet: SheetConnection,
  onSave: (String, Boolean, List<String>, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var customName by remember(sheet.id) { mutableStateOf(sheet.customName) }
  var active by remember(sheet.id) { mutableStateOf(sheet.active) }
  var maskAadhaar by remember(sheet.id) { mutableStateOf(sheet.aadhaarMaskingEnabled) }
  val selectedTabs = remember(sheet.id) {
    mutableStateListOf<String>().apply { addAll(sheet.selectedWorksheets) }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Manage Sheet Settings") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = customName,
          onValueChange = { customName = it },
          label = { Text("Custom Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Connection Active")
          Switch(checked = active, onCheckedChange = { active = it })
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Mask Aadhaar Numbers")
          Switch(checked = maskAadhaar, onCheckedChange = { maskAadhaar = it })
        }
        if (sheet.allWorksheets.isNotEmpty()) {
          Text("Worksheets:", style = MaterialTheme.typography.titleSmall)
          sheet.allWorksheets.forEach { tab ->
            val checked = tab in selectedTabs
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().clickable {
                if (checked) selectedTabs.remove(tab) else selectedTabs.add(tab)
              },
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = { c ->
                  if (c) {
                    if (tab !in selectedTabs) selectedTabs.add(tab)
                  } else {
                    selectedTabs.remove(tab)
                  }
                },
              )
              Text(tab)
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(customName, active, selectedTabs.toList(), maskAadhaar) },
        enabled = selectedTabs.isNotEmpty(),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorksheetFormConfigDialog(
  worksheet: WorksheetMetadata,
  onSave: (List<String>, Map<String, String>, Map<String, String>) -> Unit,
  onDismiss: () -> Unit,
) {
  val reqFields = remember(worksheet.id) {
    mutableStateListOf<String>().apply { addAll(worksheet.requiredFields) }
  }
  val fieldTypes = remember(worksheet.id) {
    mutableStateMapOf<String, String>().apply { putAll(worksheet.fieldTypes) }
  }
  val dropdownOptions = remember(worksheet.id) {
    mutableStateMapOf<String, String>().apply { putAll(worksheet.dropdownOptions) }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Form Rules: ${worksheet.worksheetTitle}") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (worksheet.headers.isEmpty()) {
          Text("Sync this sheet first to configure dynamic form validation rules for its columns.")
        } else {
          worksheet.headers.forEach { header ->
            val currentType =
              FieldInputType.fromRaw(fieldTypes[header] ?: FieldInputType.inferFromHeader(header).name)
            var expanded by remember { mutableStateOf(false) }

            Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(header, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Required", style = MaterialTheme.typography.bodySmall)
                    Checkbox(
                      checked = header in reqFields,
                      onCheckedChange = { req ->
                        if (req) {
                          if (header !in reqFields) reqFields.add(header)
                        } else {
                          reqFields.remove(header)
                        }
                      },
                    )
                  }
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                  OutlinedTextField(
                    value = currentType.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Field Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                  )
                  ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    FieldInputType.entries.forEach { t ->
                      DropdownMenuItem(
                        text = { Text(t.label) },
                        onClick = {
                          fieldTypes[header] = t.name
                          expanded = false
                        },
                      )
                    }
                  }
                }
                if (currentType == FieldInputType.DROPDOWN) {
                  OutlinedTextField(
                    value = dropdownOptions[header].orEmpty(),
                    onValueChange = { dropdownOptions[header] = it },
                    label = { Text("Options (comma-separated)") },
                    placeholder = { Text("Cash, Bajaj Finance, HDFC") },
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(reqFields.toList(), fieldTypes.toMap(), dropdownOptions.toMap())
        }
      ) {
        Text("Save Rules")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun CreateSpreadsheetDialog(
  isGoogleAuthorized: Boolean,
  isSyncing: Boolean,
  onAuthorizeGoogle: () -> Unit,
  onCreateConfirmed: (String, String, List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var tabTitle by remember { mutableStateOf("Records") }
  var headersCsv by remember {
    mutableStateOf("Date, Customer Name, Mobile Number, IMEI 1, Product Model, Amount")
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create New Google Sheet") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!isGoogleAuthorized) {
          Text(
            "Connect your Google Account first so Smart Sheet can create a spreadsheet in your Google Drive.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Button(onClick = onAuthorizeGoogle, modifier = Modifier.fillMaxWidth()) {
            Text("Connect Google Account")
          }
        } else {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Spreadsheet Title") },
            placeholder = { Text("e.g., Retail Sales Ledger") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = tabTitle,
            onValueChange = { tabTitle = it },
            label = { Text("Initial Worksheet Tab") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = headersCsv,
            onValueChange = { headersCsv = it },
            label = { Text("Column Headers (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      if (isGoogleAuthorized) {
        Button(
          onClick = {
            val headers = headersCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            onCreateConfirmed(title, tabTitle, headers)
          },
          enabled = title.isNotBlank() && !isSyncing,
        ) {
          Text(if (isSyncing) "Creating..." else "Create & Connect")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

