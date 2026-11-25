package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DriveSpreadsheetItem
import com.example.model.RemoteSpreadsheetPreview
import com.example.ui.theme.LocalSmartSheetColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Pure filtering helper for user-authorized Google Spreadsheets displayed in the picker.
 */
fun filterDriveSpreadsheets(
  files: List<DriveSpreadsheetItem>,
  query: String,
  onlyUnlinked: Boolean,
  connectedSpreadsheetIds: Set<String>,
): List<DriveSpreadsheetItem> {
  val trimmedQuery = query.trim().lowercase()
  return files.filter { item ->
    val matchesLinkedFilter = !onlyUnlinked || item.id !in connectedSpreadsheetIds
    val matchesQuery =
      trimmedQuery.isBlank() ||
        item.name.lowercase().contains(trimmedQuery) ||
        item.id.lowercase().contains(trimmedQuery)
    matchesLinkedFilter && matchesQuery
  }
}

/**
 * Formats an ISO-8601 Drive timestamp (e.g. `2026-11-20T04:10:00.000Z`) into a concise date string.
 */
fun formatDriveModifiedTime(isoTimestamp: String): String {
  val trimmed = isoTimestamp.trim()
  if (trimmed.isBlank()) return "Recently updated"
  val patterns =
    listOf(
      "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
      "yyyy-MM-dd'T'HH:mm:ss'Z'",
      "yyyy-MM-dd",
    )
  for (pattern in patterns) {
    try {
      val parser =
        SimpleDateFormat(pattern, Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
        }
      val parsed = parser.parse(trimmed) ?: continue
      val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
      return "Modified ${formatter.format(parsed)}"
    } catch (_: Exception) {
      // Try next timestamp format
    }
  }
  return "Modified ${trimmed.take(10)}"
}

/**
 * Material 3 ModalBottomSheet allowing authenticated users to pick from their Google Sheets
 * (via `drive.file`), open a spreadsheet by URL/ID, and select worksheet tabs to sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSheetPickerModalSheet(
  isGoogleAuthorized: Boolean,
  connectedEmail: String?,
  authError: String?,
  isLoadingDrive: Boolean,
  driveFiles: List<DriveSpreadsheetItem>,
  connectedSpreadsheetIds: Set<String>,
  preview: RemoteSpreadsheetPreview?,
  onAuthorizeGoogle: () -> Unit,
  onSearchDrive: (String) -> Unit,
  onInspectIdOrUrl: (String) -> Unit,
  onClearPreview: () -> Unit,
  onConnectConfirmed: (RemoteSpreadsheetPreview, String, List<String>) -> Unit,
  onCreateNewSheetClick: (() -> Unit)? = null,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  LaunchedEffect(isGoogleAuthorized) {
    if (isGoogleAuthorized && driveFiles.isEmpty() && !isLoadingDrive && preview == null) {
      onSearchDrive("")
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    contentWindowInsets = { WindowInsets(0) },
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.testTag("google_sheet_picker_modal"),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      GoogleSheetPickerContent(
        isGoogleAuthorized = isGoogleAuthorized,
        connectedEmail = connectedEmail,
        authError = authError,
        isLoadingDrive = isLoadingDrive,
        driveFiles = driveFiles,
        connectedSpreadsheetIds = connectedSpreadsheetIds,
        preview = preview,
        onAuthorizeGoogle = onAuthorizeGoogle,
        onSearchDrive = onSearchDrive,
        onInspectIdOrUrl = onInspectIdOrUrl,
        onClearPreview = onClearPreview,
        onConnectConfirmed = onConnectConfirmed,
        onCreateNewSheetClick = onCreateNewSheetClick,
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
      )
    }
  }
}

/**
 * Reusable Material 3 Google Sheets Picker content composable.
 * Handles unauthenticated authorization prompt, searchable spreadsheet list, URL/ID import,
 * and worksheet tab selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSheetPickerContent(
  isGoogleAuthorized: Boolean,
  connectedEmail: String?,
  authError: String?,
  isLoadingDrive: Boolean,
  driveFiles: List<DriveSpreadsheetItem>,
  connectedSpreadsheetIds: Set<String>,
  preview: RemoteSpreadsheetPreview?,
  onAuthorizeGoogle: () -> Unit,
  onSearchDrive: (String) -> Unit,
  onInspectIdOrUrl: (String) -> Unit,
  onClearPreview: () -> Unit,
  onConnectConfirmed: (RemoteSpreadsheetPreview, String, List<String>) -> Unit,
  onCreateNewSheetClick: (() -> Unit)? = null,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extended = LocalSmartSheetColors.current
  var pickerMode by remember { mutableIntStateOf(0) } // 0 = Browse Drive Sheets, 1 = URL / ID
  var driveQuery by remember { mutableStateOf("") }
  var onlyUnlinked by remember { mutableStateOf(false) }
  var idOrUrlInput by remember { mutableStateOf("") }

  val filteredFiles by remember(driveFiles, driveQuery, onlyUnlinked, connectedSpreadsheetIds) {
    derivedStateOf {
      filterDriveSpreadsheets(
        files = driveFiles,
        query = driveQuery,
        onlyUnlinked = onlyUnlinked,
        connectedSpreadsheetIds = connectedSpreadsheetIds,
      )
    }
  }

  Column(
    modifier = modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Top Header Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f),
      ) {
        if (preview != null) {
          IconButton(
            onClick = onClearPreview,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("picker_back_to_list_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to spreadsheet list",
            )
          }
        } else {
          Box(
            modifier =
              Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.TableChart,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Column {
          Text(
            text =
              when {
                !isGoogleAuthorized -> "Authorize Google Sheets"
                preview != null -> "Select Worksheets"
                else -> "Pick a Google Sheet"
              },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              when {
                !isGoogleAuthorized -> "Connect your Google Drive & Sheets first"
                !connectedEmail.isNullOrBlank() -> "Google Account: $connectedEmail"
                else -> "Authorized via drive.file & spreadsheets"
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("picker_close_button"),
      ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Close sheet picker")
      }
    }

    if (isLoadingDrive) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
    }

    if (!isGoogleAuthorized) {
      UnauthenticatedPickerCard(
        authError = authError,
        onAuthorizeGoogle = onAuthorizeGoogle,
      )
      return@Column
    }

    AnimatedContent(
      targetState = preview,
      transitionSpec = {
        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
      },
      modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
      label = "google_sheet_picker_step",
    ) { activePreview ->
      if (activePreview == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          // Segmented mode toggle: Browse Authorized Drive vs Paste Sheet URL/ID
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = pickerMode == 0,
              onClick = { pickerMode = 0 },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
              icon = {
                SegmentedButtonDefaults.Icon(active = pickerMode == 0) {
                  Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                }
              },
              modifier = Modifier.testTag("picker_mode_browse_button"),
            ) {
              Text("My Google Sheets")
            }
            SegmentedButton(
              selected = pickerMode == 1,
              onClick = { pickerMode = 1 },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
              icon = {
                SegmentedButtonDefaults.Icon(active = pickerMode == 1) {
                  Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                }
              },
              modifier = Modifier.testTag("picker_mode_url_button"),
            ) {
              Text("Paste URL / ID")
            }
          }

          if (pickerMode == 0) {
            // Search + Refresh row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = driveQuery,
                onValueChange = { driveQuery = it },
                placeholder = { Text("Filter or search your Google Sheets...") },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search spreadsheets",
                  )
                },
                trailingIcon = {
                  if (driveQuery.isNotEmpty()) {
                    IconButton(
                      onClick = {
                        driveQuery = ""
                        onSearchDrive("")
                      },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(Icons.Default.Close, contentDescription = "Clear search query")
                    }
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchDrive(driveQuery) }),
                modifier = Modifier.weight(1f).testTag("picker_search_input"),
              )
              FilledTonalButton(
                onClick = { onSearchDrive(driveQuery) },
                enabled = !isLoadingDrive,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("picker_refresh_drive_button"),
              ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Google Sheets", modifier = Modifier.size(18.dp))
              }
            }

            // Filter chip & count row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FilterChip(
                selected = onlyUnlinked,
                onClick = { onlyUnlinked = !onlyUnlinked },
                label = { Text("Hide already connected") },
                leadingIcon =
                  if (onlyUnlinked) {
                    { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                  } else {
                    null
                  },
                modifier = Modifier.testTag("picker_filter_unlinked_chip"),
              )
              Text(
                text = "${filteredFiles.size} sheet${if (filteredFiles.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            // Scrollable Spreadsheet List
            if (isLoadingDrive && driveFiles.isEmpty()) {
              Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  CircularProgressIndicator()
                  Text(
                    text = "Loading your Google Sheets...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else if (filteredFiles.isEmpty()) {
              OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(20.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                  )
                  Text(
                    text =
                      if (driveFiles.isEmpty()) "No Authorized Sheets Yet"
                      else "No Matching Sheets Found",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  Text(
                    text =
                      if (driveFiles.isEmpty()) {
                        "Create a new Google Sheet or switch to Paste URL / ID to link an existing spreadsheet."
                      } else {
                        "Try clearing your filter or search Google Drive directly."
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    OutlinedButton(
                      onClick = { pickerMode = 1 },
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                    ) {
                      Text("Paste URL / ID")
                    }
                    if (onCreateNewSheetClick != null) {
                      Button(
                        onClick = {
                          onDismiss()
                          onCreateNewSheetClick()
                        },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("picker_create_sheet_button"),
                      ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Create Sheet")
                      }
                    }
                  }
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 120.dp, max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
              ) {
                items(filteredFiles, key = { it.id }) { file ->
                  val alreadyLinked = file.id in connectedSpreadsheetIds
                  DriveSpreadsheetRowCard(
                    file = file,
                    isAlreadyConnected = alreadyLinked,
                    healthyColor = extended.healthy,
                    enabled = !isLoadingDrive,
                    onClick = { onInspectIdOrUrl(file.id) },
                  )
                }
              }
            }
          } else {
            // Mode 1: Direct URL / Spreadsheet ID entry
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
              Text(
                text = "Paste a full Google Sheets URL (docs.google.com/spreadsheets/d/...) or Spreadsheet ID to inspect its tabs and connect it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
              )
            }
            OutlinedTextField(
              value = idOrUrlInput,
              onValueChange = { idOrUrlInput = it },
              label = { Text("Google Spreadsheet URL or ID") },
              placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("picker_url_input"),
            )
            Button(
              onClick = { onInspectIdOrUrl(idOrUrlInput) },
              enabled = idOrUrlInput.isNotBlank() && !isLoadingDrive,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("picker_load_url_button"),
            ) {
              Text(if (isLoadingDrive) "Loading Spreadsheet..." else "Inspect & Load Worksheets")
            }
          }
        }
      } else {
        WorksheetSelectionStep(
          preview = activePreview,
          onConnectConfirmed = onConnectConfirmed,
          onBack = onClearPreview,
        )
      }
    }
  }
}

@Composable
private fun UnauthenticatedPickerCard(
  authError: String?,
  onAuthorizeGoogle: () -> Unit,
) {
  val extended = LocalSmartSheetColors.current
  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Default.VerifiedUser,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
        )
        Column {
          Text(
            text = "Google Drive & Sheets Authorization Needed",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Grant drive.file and spreadsheets access to browse and connect your Google Sheets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (!authError.isNullOrBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = extended.error.copy(alpha = 0.12f),
        ) {
          Text(
            text = authError,
            style = MaterialTheme.typography.bodySmall,
            color = extended.error,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
          )
        }
      }

      Button(
        onClick = onAuthorizeGoogle,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("picker_authorize_google_button"),
      ) {
        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text("Authorize Google Account")
      }
    }
  }
}

@Composable
private fun DriveSpreadsheetRowCard(
  file: DriveSpreadsheetItem,
  isAlreadyConnected: Boolean,
  healthyColor: Color,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  OutlinedCard(
    onClick = onClick,
    enabled = enabled,
    shape = MaterialTheme.shapes.medium,
    border =
      BorderStroke(
        1.dp,
        if (isAlreadyConnected) healthyColor.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outlineVariant,
      ),
    colors =
      CardDefaults.outlinedCardColors(
        containerColor =
          if (isAlreadyConnected) healthyColor.copy(alpha = 0.05f)
          else MaterialTheme.colorScheme.surface,
      ),
    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("drive_sheet_item_${file.id}"),
  ) {
    ListItem(
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      leadingContent = {
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(healthyColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Default.TableChart,
            contentDescription = null,
            tint = healthyColor,
            modifier = Modifier.size(22.dp),
          )
        }
      },
      headlineContent = {
        Text(
          text = file.name.ifBlank { "Untitled Spreadsheet" },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = formatDriveModifiedTime(file.modifiedTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
          Text(
            text = "ID: ${file.id}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      },
      trailingContent = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (isAlreadyConnected) {
            StatusPill(
              label = "LINKED",
              containerColor = healthyColor.copy(alpha = 0.15f),
              contentColor = healthyColor,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Select ${file.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
    )
  }
}

@Composable
private fun WorksheetSelectionStep(
  preview: RemoteSpreadsheetPreview,
  onConnectConfirmed: (RemoteSpreadsheetPreview, String, List<String>) -> Unit,
  onBack: () -> Unit,
) {
  var customName by remember(preview.spreadsheetId) { mutableStateOf(preview.title) }
  val selectedTabs =
    remember(preview.spreadsheetId) {
      mutableStateListOf<String>().apply {
        preview.worksheets.firstOrNull()?.let { add(it.title) }
      }
    }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
          Text(
            text = preview.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${preview.worksheets.size} worksheet tab(s) available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      OutlinedTextField(
        value = customName,
        onValueChange = { customName = it },
        label = { Text("Display Name in Smart Sheet") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("picker_custom_name_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Worksheets to Index (${selectedTabs.size}/${preview.worksheets.size})",
          style = MaterialTheme.typography.titleSmall,
        )
        if (preview.worksheets.size > 1) {
          val allSelected = selectedTabs.size == preview.worksheets.size
          TextButton(
            onClick = {
              selectedTabs.clear()
              if (!allSelected) {
                selectedTabs.addAll(preview.worksheets.map { it.title })
              }
            },
            modifier = Modifier.testTag("picker_select_all_tabs_button"),
          ) {
            Text(if (allSelected) "Clear All" else "Select All")
          }
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column {
          preview.worksheets.forEachIndexed { idx, ws ->
            val checked = ws.title in selectedTabs
            ListItem(
              headlineContent = {
                Text(ws.title, style = MaterialTheme.typography.bodyLarge)
              },
              supportingContent = {
                Text(
                  "${ws.rowCount} rows × ${ws.columnCount} columns",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              },
              leadingContent = {
                Checkbox(
                  checked = checked,
                  onCheckedChange = null,
                )
              },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { isChecked ->
                      if (isChecked) {
                        if (ws.title !in selectedTabs) selectedTabs.add(ws.title)
                      } else {
                        selectedTabs.remove(ws.title)
                      }
                    },
                  )
                  .testTag("picker_worksheet_item_${ws.title}"),
            )
            if (idx < preview.worksheets.lastIndex) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onBack,
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
      ) {
        Text("Back")
      }
      Button(
        onClick = { onConnectConfirmed(preview, customName.ifBlank { preview.title }, selectedTabs.toList()) },
        enabled = selectedTabs.isNotEmpty(),
        modifier = Modifier.weight(1.4f).minimumInteractiveComponentSize().testTag("picker_connect_confirm_button"),
      ) {
        Text("Connect & Sync")
      }
    }
  }
}
