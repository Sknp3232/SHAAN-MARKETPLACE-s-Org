package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FieldInputType
import com.example.model.SheetConnection
import com.example.model.UiState
import com.example.model.WorksheetMetadata
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
  viewModel: MainWorkspaceViewModel,
  preselectedSheetId: String? = null,
  onConnectGoogleAuthClick: () -> Unit,
  onNavigateToSheets: () -> Unit,
  onRecordAdded: () -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val worksheetsUi by viewModel.worksheetsState.collectAsStateWithLifecycle()
  val isGoogleAuthorized by viewModel.isGoogleAuthorized.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

  val allSheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val writableSheets = remember(allSheets, viewModel.isAdmin) {
    allSheets.filter { it.active && viewModel.canAddToSheet(it.id) }
  }
  val allWorksheets = (worksheetsUi as? UiState.Success)?.data.orEmpty()

  var selectedSheet by remember(writableSheets, preselectedSheetId) {
    mutableStateOf(
      writableSheets.firstOrNull { it.id == preselectedSheetId } ?: writableSheets.firstOrNull()
    )
  }
  val sheetWorksheets =
    remember(selectedSheet, allWorksheets) {
      allWorksheets.filter { it.sheetConnectionId == selectedSheet?.id }
    }
  var selectedWorksheet by remember(sheetWorksheets) {
    mutableStateOf(sheetWorksheets.firstOrNull())
  }

  var step by remember { mutableIntStateOf(0) } // 0 = Form Entry, 1 = Preview & Submit
  var sheetDropdownExpanded by remember { mutableStateOf(false) }
  var wsDropdownExpanded by remember { mutableStateOf(false) }
  var scannerTargetHeader by remember { mutableStateOf<String?>(null) }

  val formValues = remember(selectedWorksheet?.id) { mutableStateMapOf<String, String>() }
  val fieldErrors = remember(selectedWorksheet?.id) { mutableStateMapOf<String, String>() }
  val duplicateWarnings = remember(selectedWorksheet?.id) { mutableStateMapOf<String, String>() }
  var lastInsertedMessage by remember { mutableStateOf<String?>(null) }

  // Check duplicate IMEI reactively with debounce as IMEI fields change
  val headers = selectedWorksheet?.headers.orEmpty()
  LaunchedEffect(formValues.toMap(), selectedWorksheet?.id) {
    val ws = selectedWorksheet ?: return@LaunchedEffect
    delay(300L)
    for (header in ws.headers) {
      val type = FieldInputType.fromRaw(ws.fieldTypes[header])
      if (type == FieldInputType.IMEI || header.lowercase().contains("imei")) {
        val valDigits = formValues[header].orEmpty().filter { it.isDigit() }
        if (valDigits.length == 15) {
          val matches = viewModel.checkDuplicateImei(valDigits)
          if (matches.isNotEmpty()) {
            val m = matches.first()
            duplicateWarnings[header] =
              "Duplicate IMEI found in ${m.spreadsheetName}/${m.worksheetTitle} (Row #${m.rowNumber})"
          } else {
            duplicateWarnings.remove(header)
          }
        } else {
          duplicateWarnings.remove(header)
        }
      }
    }
  }

  if (scannerTargetHeader != null) {
    val target = scannerTargetHeader!!
    ImeiScannerModal(
      title = "Scan $target",
      initialValue = formValues[target].orEmpty(),
      checkDuplicate = { imei -> viewModel.checkDuplicateImei(imei) },
      onConfirm = { scanned ->
        formValues[target] = scanned
        fieldErrors.remove(target)
        scannerTargetHeader = null
      },
      onDismiss = { scannerTargetHeader = null },
    )
  }

  fun validateForm(ws: WorksheetMetadata): Boolean {
    fieldErrors.clear()
    var valid = true
    for (header in ws.headers) {
      val value = formValues[header].orEmpty().trim()
      val isRequired = header in ws.requiredFields
      val type =
        ws.fieldTypes[header]?.let { FieldInputType.fromRaw(it) }
          ?: FieldInputType.inferFromHeader(header)

      if (isRequired && value.isEmpty()) {
        fieldErrors[header] = "$header is required"
        valid = false
        continue
      }
      if (value.isNotEmpty()) {
        when (type) {
          FieldInputType.IMEI -> {
            if (!SearchEngine.isValidImei15(value)) {
              fieldErrors[header] = "IMEI must be exactly 15 digits"
              valid = false
            }
          }
          FieldInputType.MOBILE -> {
            val digits = value.filter { it.isDigit() }
            if (digits.length < 10) {
              fieldErrors[header] = "Enter at least 10 mobile digits"
              valid = false
            }
          }
          FieldInputType.AADHAAR -> {
            val digits = value.filter { it.isDigit() }
            if (digits.length != 12) {
              fieldErrors[header] = "Aadhaar must be 12 digits"
              valid = false
            }
          }
          FieldInputType.NUMBER -> {
            if (value.toDoubleOrNull() == null) {
              fieldErrors[header] = "Enter a valid number"
              valid = false
            }
          }
          else -> {}
        }
      }
    }
    return valid
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
          Text(text = "+ Add New Record", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Direct Google Sheets insertion preserving column order",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          label = if (step == 0) "STEP 1: ENTRY" else "STEP 2: REVIEW",
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.primary,
        )
      }
    }

    if (!lastInsertedMessage.isNullOrBlank()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = extended.healthy.copy(alpha = 0.12f)),
          border = BorderStroke(1.dp, extended.healthy),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = extended.healthy)
            Text(
              text = lastInsertedMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    if (!isGoogleAuthorized) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (viewModel.isAdmin) {
              Text(
                text = "Authorize Google Sheets access to insert rows directly.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              Button(onClick = onConnectGoogleAuthClick, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text("Authorize")
              }
            } else {
              Text(
                text = "Submissions are routed securely through your Admin's connected Google Sheets workspace.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
    }

    if (writableSheets.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Writable Sheets Available",
          message =
            if (viewModel.isAdmin) {
              "Connect an active Google Spreadsheet first, then select a worksheet to load its dynamic entry form."
            } else {
              "You currently do not have Search + Add permission on any active sheet. Ask your Admin to grant Add access."
            },
          actionLabel = if (viewModel.isAdmin) "Manage Sheets" else null,
          onAction = if (viewModel.isAdmin) onNavigateToSheets else null,
        )
      }
      return@LazyColumn
    }

    // Sheet & Worksheet Selectors
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ExposedDropdownMenuBox(
            expanded = sheetDropdownExpanded,
            onExpandedChange = { sheetDropdownExpanded = it },
          ) {
            OutlinedTextField(
              value = selectedSheet?.customName ?: "Select Sheet",
              onValueChange = {},
              readOnly = true,
              label = { Text("Target Google Sheet") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sheetDropdownExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = sheetDropdownExpanded,
              onDismissRequest = { sheetDropdownExpanded = false },
            ) {
              writableSheets.forEach { s ->
                DropdownMenuItem(
                  text = { Text("${s.customName} (${s.originalTitle})") },
                  onClick = {
                    selectedSheet = s
                    step = 0
                    sheetDropdownExpanded = false
                  },
                )
              }
            }
          }

          ExposedDropdownMenuBox(
            expanded = wsDropdownExpanded,
            onExpandedChange = { wsDropdownExpanded = it },
          ) {
            OutlinedTextField(
              value = selectedWorksheet?.worksheetTitle ?: "Select Worksheet",
              onValueChange = {},
              readOnly = true,
              label = { Text("Worksheet / Tab") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wsDropdownExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = wsDropdownExpanded,
              onDismissRequest = { wsDropdownExpanded = false },
            ) {
              sheetWorksheets.forEach { ws ->
                DropdownMenuItem(
                  text = { Text("${ws.worksheetTitle} (${ws.headers.size} columns)") },
                  onClick = {
                    selectedWorksheet = ws
                    step = 0
                    wsDropdownExpanded = false
                  },
                )
              }
            }
          }
        }
      }
    }

    val currentSheet = selectedSheet
    val currentWs = selectedWorksheet
    if (currentSheet == null || currentWs == null) return@LazyColumn

    if (headers.isEmpty()) {
      item {
        EmptyStateCard(
          title = "Column Headers Not Loaded Yet",
          message = "Synchronize '${currentSheet.customName}' to automatically load column headers for '${currentWs.worksheetTitle}'.",
          actionLabel = if (viewModel.isAdmin || isGoogleAuthorized) "Sync Sheet Now" else "Refresh Workspace",
          onAction = {
            if (viewModel.isAdmin && !isGoogleAuthorized) onConnectGoogleAuthClick()
            else viewModel.syncSheet(currentSheet)
          },
          showIllustration = false,
        )
      }
      return@LazyColumn
    }

    if (step == 0) {
      // Dynamic Form Fields auto-generated from existing sheet headers
      itemsIndexed(
        items = headers,
        key = { idx, header -> "${currentWs.id}_${idx}_$header" },
        contentType = { _, _ -> "form_field" },
      ) { _, header ->
        val type =
          currentWs.fieldTypes[header]?.let { FieldInputType.fromRaw(it) }
            ?: FieldInputType.inferFromHeader(header)
        val isRequired = header in currentWs.requiredFields
        val dropdownOpts =
          currentWs.dropdownOptions[header]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val value = formValues[header].orEmpty()
        val errorText = fieldErrors[header]
        val dupWarning = duplicateWarnings[header]

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (type == FieldInputType.DROPDOWN && dropdownOpts.isNotEmpty()) {
            var optExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
              expanded = optExpanded,
              onExpandedChange = { optExpanded = it },
            ) {
              OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(if (isRequired) "$header *" else header) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = optExpanded) },
                isError = errorText != null,
                supportingText = errorText?.let { err -> { Text(err) } },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = optExpanded,
                onDismissRequest = { optExpanded = false },
              ) {
                dropdownOpts.forEach { opt ->
                  DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                      formValues[header] = opt
                      fieldErrors.remove(header)
                      optExpanded = false
                    },
                  )
                }
              }
            }
          } else {
            val kbType =
              when (type) {
                FieldInputType.NUMBER, FieldInputType.IMEI, FieldInputType.AADHAAR ->
                  KeyboardType.Number
                FieldInputType.MOBILE -> KeyboardType.Phone
                else -> KeyboardType.Text
              }
            OutlinedTextField(
              value = value,
              onValueChange = {
                formValues[header] = it
                fieldErrors.remove(header)
              },
              label = { Text(if (isRequired) "$header *" else header) },
              placeholder = {
                Text(
                  when (type) {
                    FieldInputType.IMEI -> "15-digit IMEI or scan"
                    FieldInputType.DATE -> "YYYY-MM-DD or DD/MM/YYYY"
                    FieldInputType.MOBILE -> "10-digit mobile number"
                    FieldInputType.AADHAAR -> "12-digit Aadhaar number"
                    else -> "Enter $header"
                  }
                )
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = kbType),
              isError = errorText != null,
              supportingText = {
                if (errorText != null) {
                  Text(errorText, color = MaterialTheme.colorScheme.error)
                } else {
                  Text(type.label, style = MaterialTheme.typography.bodySmall)
                }
              },
              trailingIcon = {
                if (type == FieldInputType.IMEI || header.lowercase().contains("imei")) {
                  IconButton(
                    onClick = { scannerTargetHeader = header },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_$header"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.QrCodeScanner,
                      contentDescription = "Scan $header barcode",
                      tint = MaterialTheme.colorScheme.secondary,
                    )
                  }
                }
              },
              modifier = Modifier.fillMaxWidth().testTag("field_$header"),
            )
          }

          if (dupWarning != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = extended.warning.copy(alpha = 0.14f),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Default.Warning,
                  contentDescription = null,
                  tint = extended.warning,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = dupWarning,
                  style = MaterialTheme.typography.bodySmall,
                  color = extended.warning,
                )
              }
            }
          }
        }
      }

      item {
        Button(
          onClick = {
            if (validateForm(currentWs)) {
              step = 1
            } else {
              viewModel.emitToast("Please fix highlighted form fields before reviewing.")
            }
          },
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("review_record_button"),
        ) {
          Text("Review Before Submission")
          Spacer(Modifier.size(6.dp))
          Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
      }
    } else {
      // Step 2: Review & Edit before submission
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Confirm Record Details",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "Inserting into ${currentSheet.customName} / ${currentWs.worksheetTitle}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
            headers.forEach { h ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = h,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(0.45f),
                )
                Text(
                  text = formValues[h].orEmpty().ifBlank { "—" },
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(0.55f),
                )
              }
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedButton(
            onClick = { step = 0 },
            enabled = !isSyncing,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Edit Form")
          }
          Button(
            onClick = {
              val orderedValues = headers.map { h -> formValues[h].orEmpty().trim() }
              viewModel.submitNewRecord(
                sheet = currentSheet,
                worksheet = currentWs,
                orderedValues = orderedValues,
                onSuccess = { rowNum ->
                  lastInsertedMessage =
                    "Added Row #$rowNum to ${currentSheet.customName} / ${currentWs.worksheetTitle}. Now searchable!"
                  formValues.clear()
                  step = 0
                  onRecordAdded()
                },
              )
            },
            enabled = !isSyncing,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("submit_record_button"),
          ) {
            Text(if (isSyncing) "Inserting..." else "Submit to Sheet")
          }
        }
      }
    }
  }
}
