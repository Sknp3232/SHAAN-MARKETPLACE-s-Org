package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
  recordId: String,
  viewModel: MainWorkspaceViewModel,
  onBack: () -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  val selectedRecord by viewModel.selectedRecord.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val settings by viewModel.settingsState.collectAsStateWithLifecycle()

  LaunchedEffect(recordId) { viewModel.selectRecord(recordId) }

  var revealAadhaar by remember { mutableStateOf(false) }
  var isEditing by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val record = selectedRecord
  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val parentSheet = record?.let { r -> sheets.firstOrNull { it.id == r.sheetConnectionId } }
  val canEdit = record != null && parentSheet != null && viewModel.canEditSheet(parentSheet.id)

  if (showDeleteConfirm && record != null && parentSheet != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Row #${record.rowNumber}?") },
      text = {
        Text(
          "This permanently deletes row #${record.rowNumber} from '${record.worksheetTitle}' in your Google Sheet while preserving all formatting and other rows."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            viewModel.deleteExistingRecord(record, parentSheet) {
              onBack()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = extended.error),
        ) {
          Text("Delete Row")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Record Details") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (canEdit && !isEditing) {
            IconButton(
              onClick = { isEditing = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_record_button"),
            ) {
              Icon(Icons.Default.Edit, contentDescription = "Edit record")
            }
            if (record != null && record.rowNumber >= 2) {
              IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_record_button"),
              ) {
                Icon(
                  Icons.Default.DeleteOutline,
                  contentDescription = "Delete row",
                  tint = extended.error,
                )
              }
            }
          }
        },
      )
    }
  ) { padding ->
    if (record == null) {
      Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        EmptyStateCard(
          title = "Record Not Available",
          message = "This record could not be loaded from the synchronized workspace.",
          actionLabel = "Go Back",
          onAction = onBack,
        )
      }
      return@Scaffold
    }

    if (isEditing && parentSheet != null) {
      val editableValues = remember(record.id) {
        mutableStateListOf<String>().apply {
          record.headers.forEachIndexed { idx, _ -> add(record.cells.getOrNull(idx).orEmpty()) }
        }
      }
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Text(
            text = "Editing Row #${record.rowNumber} in ${record.worksheetTitle}",
            style = MaterialTheme.typography.titleLarge,
          )
        }
        itemsIndexed(
          items = record.headers,
          key = { idx, h -> "${record.id}_edit_${idx}_$h" },
          contentType = { _, _ -> "edit_field" },
        ) { idx, header ->
          OutlinedTextField(
            value = editableValues.getOrElse(idx) { "" },
            onValueChange = { if (idx < editableValues.size) editableValues[idx] = it },
            label = { Text(header) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            OutlinedButton(
              onClick = { isEditing = false },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Text("Cancel")
            }
            Button(
              onClick = {
                viewModel.updateExistingRecord(
                  record = record,
                  sheet = parentSheet,
                  orderedValues = editableValues.toList(),
                  onSuccess = { isEditing = false },
                )
              },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_record_edit_button"),
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(6.dp))
              Text("Save to Sheet")
            }
          }
        }
      }
    } else {
      val cellEntries = remember(record.id, record.cells) { record.cellMap() }
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Source Information Card
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = record.customerName.ifBlank { "Row #${record.rowNumber}" },
                  style = MaterialTheme.typography.headlineMedium,
                  modifier = Modifier.weight(1f),
                )
                StatusPill(
                  label = if (record.isFromCache) "CACHED" else "LIVE SYNCED",
                  containerColor =
                    if (record.isFromCache) extended.warning.copy(alpha = 0.14f)
                    else extended.healthy.copy(alpha = 0.14f),
                  contentColor = if (record.isFromCache) extended.warning else extended.healthy,
                )
              }
              Text(
                text = "Google Sheet: ${parentSheet?.originalTitle ?: record.spreadsheetName}",
                style = MaterialTheme.typography.bodyMedium,
              )
              Text(
                text = "App Label: ${record.spreadsheetName}  •  Tab: ${record.worksheetTitle}  •  Row #${record.rowNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Last Synced: ${SearchEngine.formatTimestamp(record.syncedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Application-level Audit Information (if tracked — never invented!)
        if (record.appAudit != null) {
          val audit = record.appAudit
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = "Application Audit Trail (${audit.actionType})",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Recorded by: ${audit.addedByName} (${audit.addedByEmail})",
                  style = MaterialTheme.typography.bodySmall,
                )
                Text(
                  text = "Timestamp: ${SearchEngine.formatTimestamp(audit.createdAt?.toDate()?.time)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        // All Original Sheet Columns in Exact Order
        item {
          Text(
            text = "All Sheet Columns (${record.headers.size})",
            style = MaterialTheme.typography.titleLarge,
          )
        }

        itemsIndexed(
          items = cellEntries,
          key = { idx, (h, _) -> "${record.id}_view_${idx}_$h" },
          contentType = { _, _ -> "detail_column" },
        ) { idx, (header, rawVal) ->
          val isAadhaar = header.lowercase().contains("aadhaar") || header.lowercase().contains("aadhar")
          val displayVal =
            if (isAadhaar && settings.maskAadhaarByDefault && !revealAadhaar) {
              record.displayAadhaar(maskByDefault = true, revealFull = false)
            } else {
              rawVal.ifBlank { "—" }
            }

          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = header,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = displayVal,
                  style =
                    if (header.lowercase().contains("imei") || isAadhaar || header.lowercase().contains("invoice")) {
                      MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                    } else {
                      MaterialTheme.typography.bodyLarge
                    },
                )
              }
              if (isAadhaar && viewModel.canViewFullAadhaar() && settings.maskAadhaarByDefault) {
                IconButton(
                  onClick = { revealAadhaar = !revealAadhaar },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("toggle_aadhaar_visibility"),
                ) {
                  Icon(
                    imageVector =
                      if (revealAadhaar) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (revealAadhaar) "Mask Aadhaar" else "Reveal Aadhaar",
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
