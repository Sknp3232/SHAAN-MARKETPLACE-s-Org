package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.AccountStatus
import com.example.model.EmployeeRecord
import com.example.model.Invitation
import com.example.model.InvitationStatus
import com.example.model.SheetConnection
import com.example.model.SheetPermissionLevel
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSmartSheetColors
import com.example.util.AppLinksConfig
import com.example.util.InvitationValidator
import com.example.util.SearchEngine
import com.example.viewmodel.MainWorkspaceViewModel

@Composable
fun EmployeesScreen(
  viewModel: MainWorkspaceViewModel,
  onOpenEmployeeDetail: (String) -> Unit,
  onOpenPermissionsMatrix: () -> Unit,
) {
  val context = LocalContext.current
  val extended = LocalSmartSheetColors.current
  val employeesUi by viewModel.employeesState.collectAsStateWithLifecycle()
  val invitationsUi by viewModel.invitationsState.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()

  val employees = (employeesUi as? UiState.Success)?.data.orEmpty()
  val invitations = (invitationsUi as? UiState.Success)?.data.orEmpty()
  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()

  var showInviteDialog by remember { mutableStateOf(false) }
  var createdInvitationForDispatch by remember { mutableStateOf<Invitation?>(null) }
  var filterStatus by remember { mutableStateOf<AccountStatus?>(null) }
  var searchQuery by remember { mutableStateOf("") }

  var inviteSubmitError by remember { mutableStateOf<String?>(null) }

  if (showInviteDialog) {
    CreateInviteDialog(
      sheets = sheets,
      submitError = inviteSubmitError,
      onCreateInvite = { targetEmail, defaultPerm, assignedSheetIds, sheetPermissions, maxUses, expiryDays ->
        inviteSubmitError = null
        viewModel.createInvitation(
          targetEmail = targetEmail,
          defaultPermission = defaultPerm,
          assignedSheetIds = assignedSheetIds,
          sheetPermissions = sheetPermissions,
          maxUses = maxUses,
          expiryDays = expiryDays,
          onError = { err -> inviteSubmitError = err },
          onCreated = { createdInv ->
            showInviteDialog = false
            createdInvitationForDispatch = createdInv
          },
        )
      },
      onDismiss = {
        inviteSubmitError = null
        showInviteDialog = false
      },
    )
  }

  createdInvitationForDispatch?.let { createdInv ->
    InviteDispatchDialog(
      invitation = createdInv,
      onSendEmail = {
        val url = AppLinksConfig.buildInviteUrl(context, createdInv.inviteCode)
        AppLinksConfig.launchInviteEmail(
          context = context,
          targetEmail = createdInv.normalizedTargetEmail(),
          adminName = viewModel.userProfile.displayName,
          inviteLink = url,
          inviteCode = createdInv.inviteCode,
          sheetCount = createdInv.assignedSheetIds.size,
        )
      },
      onShareLink = {
        val url = AppLinksConfig.buildInviteUrl(context, createdInv.inviteCode)
        AppLinksConfig.shareLink(
          context = context,
          title = "Smart Sheet Invitation (${createdInv.normalizedTargetEmail()})",
          url = url,
        )
      },
      onCopyLink = {
        val url = AppLinksConfig.buildInviteUrl(context, createdInv.inviteCode)
        if (AppLinksConfig.copyLinkToClipboard(context, "Smart Sheet Invite", url)) {
          viewModel.emitToast("Copied invitation link for ${createdInv.normalizedTargetEmail()}")
        }
      },
      onDismiss = { createdInvitationForDispatch = null },
    )
  }

  val filteredEmployees =
    remember(employees, filterStatus, searchQuery) {
      employees.filter { emp ->
        val matchesStatus = filterStatus == null || emp.resolvedStatus() == filterStatus
        val q = searchQuery.trim().lowercase()
        val matchesQuery =
          q.isEmpty() ||
            emp.name.lowercase().contains(q) ||
            emp.email.lowercase().contains(q) ||
            emp.employeeCode.lowercase().contains(q)
        matchesStatus && matchesQuery
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Employees & Invitations", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Invite employees via secure links or codes and control access per spreadsheet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = { showInviteDialog = true },
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("invite_employee_button"),
        ) {
          Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(6.dp))
          Text("Invite Employee")
        }
        OutlinedButton(
          onClick = onOpenPermissionsMatrix,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("open_permissions_matrix_button"),
        ) {
          Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.size(6.dp))
          Text("Permissions")
        }
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search employees by name, email, or ID") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val statuses: List<AccountStatus?> = listOf(null, AccountStatus.ACTIVE, AccountStatus.SUSPENDED)
        statuses.forEach { st ->
          val selected = filterStatus == st
          val label = st?.name ?: "ALL (${employees.size})"
          FilledTonalButton(
            onClick = { filterStatus = st },
            colors =
              if (selected) {
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.primary,
                )
              } else {
                ButtonDefaults.filledTonalButtonColors()
              },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text(label, maxLines = 1)
          }
        }
      }
    }

    // Active & Recent Invitations
    if (invitations.isNotEmpty()) {
      item {
        Text(text = "Invitation Links & Codes", style = MaterialTheme.typography.titleLarge)
      }
      items(invitations, key = { it.inviteCode }, contentType = { "invitation_card" }) { inv ->
        val status = inv.resolvedStatus()
        val inviteLink = AppLinksConfig.buildInviteUrl(context, inv.inviteCode)
        val emailLabel = inv.normalizedTargetEmail().ifBlank { "Unassigned email" }
        val roleLabel = inv.resolvedRole().name
        val perSheetSummary =
          remember(inv.assignedSheetIds, inv.sheetPermissions, inv.defaultPermission) {
            if (inv.assignedSheetIds.isEmpty()) "0 sheets assigned"
            else "${inv.assignedSheetIds.size} sheets (${inv.resolvedPermission().label})"
          }
        Card(
          modifier = Modifier.fillMaxWidth(),
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
                Text(
                  text = emailLabel,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Code: ${inv.inviteCode} • Role: $roleLabel • $perSheetSummary",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              StatusPill(
                label = status.name,
                containerColor =
                  if (status == InvitationStatus.ACTIVE) extended.healthy.copy(alpha = 0.14f)
                  else extended.error.copy(alpha = 0.14f),
                contentColor =
                  if (status == InvitationStatus.ACTIVE) extended.healthy else extended.error,
              )
            }
            Text(
              text = "Invite Link: $inviteLink",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (inv.expiresAt != null) {
              Text(
                text = "Expires: ${SearchEngine.formatTimestamp(inv.expiresAt.toDate().time)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (inv.normalizedTargetEmail().isNotBlank() && status == InvitationStatus.ACTIVE) {
                FilledTonalButton(
                  onClick = {
                    AppLinksConfig.launchInviteEmail(
                      context = context,
                      targetEmail = inv.normalizedTargetEmail(),
                      adminName = viewModel.userProfile.displayName,
                      inviteLink = inviteLink,
                      inviteCode = inv.inviteCode,
                      sheetCount = inv.assignedSheetIds.size,
                    )
                  },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.size(4.dp))
                  Text("Email", maxLines = 1)
                }
              }
              OutlinedButton(
                onClick = {
                  if (AppLinksConfig.copyLinkToClipboard(context, "Smart Sheet Invite", inviteLink)) {
                    viewModel.emitToast("Copied invite link for ${inv.inviteCode}")
                  }
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Copy", maxLines = 1)
              }
              OutlinedButton(
                onClick = {
                  AppLinksConfig.shareLink(
                    context = context,
                    title = "Smart Sheet Invitation",
                    url = inviteLink,
                  )
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Share", maxLines = 1)
              }
              if (status == InvitationStatus.ACTIVE) {
                IconButton(
                  onClick = { viewModel.revokeInvitation(inv.inviteCode) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    Icons.Default.Block,
                    contentDescription = "Revoke invitation",
                    tint = extended.error,
                  )
                }
              }
            }
          }
        }
      }
    }

    item {
      Text(
        text = "Registered Employees (${filteredEmployees.size})",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    if (filteredEmployees.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Employees Yet",
          message =
            "Generate an invitation code or link above and share it with an employee. When they sign in with Google using the invite, they will appear here automatically.",
          actionLabel = "Invite First Employee",
          onAction = { showInviteDialog = true },
          showIllustration = false,
        )
      }
    } else {
      items(filteredEmployees, key = { it.employeeUid }, contentType = { "employee_card" }) { emp ->
        val status = emp.resolvedStatus()
        Card(
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.medium)
              .clickable { onOpenEmployeeDetail(emp.employeeUid) }
              .testTag("employee_card_${emp.employeeUid}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              EmployeeAvatar(name = emp.name, photoUrl = emp.photoUrl)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = emp.name.ifBlank { emp.email },
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = emp.email,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "ID: ${emp.employeeCode.ifBlank { emp.employeeUid.take(8).uppercase() }}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              StatusPill(
                label = status.name,
                containerColor =
                  when (status) {
                    AccountStatus.ACTIVE -> extended.healthy.copy(alpha = 0.14f)
                    AccountStatus.SUSPENDED -> extended.warning.copy(alpha = 0.16f)
                    AccountStatus.REMOVED -> extended.error.copy(alpha = 0.14f)
                  },
                contentColor =
                  when (status) {
                    AccountStatus.ACTIVE -> extended.healthy
                    AccountStatus.SUSPENDED -> extended.warning
                    AccountStatus.REMOVED -> extended.error
                  },
              )
            }

            Text(
              text =
                "${emp.assignedSheetIds.size} sheets • ${emp.resolvedDefaultPermission().label} • " +
                  "${emp.totalSearches} searches • ${emp.totalRecordsAdded} records added",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text =
                "Added by: ${emp.addedByAdminName} • Joined: ${SearchEngine.formatTimestamp(emp.joinedAt?.toDate()?.time)} • " +
                  "Last active: ${SearchEngine.formatRelativeTime(emp.lastActiveAt?.toDate()?.time)}",
              style = MaterialTheme.typography.bodySmall,
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
fun EmployeeDetailScreen(
  employeeUid: String,
  viewModel: MainWorkspaceViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val extended = LocalSmartSheetColors.current
  val employeesUi by viewModel.employeesState.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val logsUi by viewModel.activityLogsState.collectAsStateWithLifecycle()
  val auditsUi by viewModel.recordAuditsState.collectAsStateWithLifecycle()

  val employee = (employeesUi as? UiState.Success)?.data?.firstOrNull { it.employeeUid == employeeUid }
  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()
  val logsAll = (logsUi as? UiState.Success)?.data.orEmpty()
  val auditsAll = (auditsUi as? UiState.Success)?.data.orEmpty()
  val empLogs = remember(logsAll, employeeUid) { logsAll.filter { it.userId == employeeUid } }
  val empAudits = remember(auditsAll, employeeUid) { auditsAll.filter { it.addedByUid == employeeUid } }

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showRemoveConfirm by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(employee?.name?.ifBlank { "Employee Profile" } ?: "Employee Details") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (employee != null) {
            IconButton(
              onClick = { showEditProfileDialog = true },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Default.Edit, contentDescription = "Edit employee info")
            }
          }
        },
      )
    }
  ) { padding ->
    if (employee == null) {
      Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        contentAlignment = Alignment.Center,
      ) {
        EmptyStateCard(
          title = "Employee Not Found",
          message = "This employee record may have been removed.",
          actionLabel = "Go Back",
          onAction = onBack,
          showIllustration = false,
        )
      }
      return@Scaffold
    }

    var defaultPerm by remember(employee.employeeUid, employee.defaultPermission) {
      mutableStateOf(employee.resolvedDefaultPermission())
    }
    var permDropdownExpanded by remember { mutableStateOf(false) }
    val assignedSheets = remember(employee.employeeUid, employee.assignedSheetIds) {
      mutableStateListOf<String>().apply { addAll(employee.assignedSheetIds) }
    }
    val sheetPerms = remember(employee.employeeUid, employee.sheetPermissions) {
      mutableStateMapOf<String, String>().apply { putAll(employee.sheetPermissions) }
    }

    if (showEditProfileDialog) {
      var nameInput by remember { mutableStateOf(employee.name) }
      var emailInput by remember { mutableStateOf(employee.email) }
      AlertDialog(
        onDismissRequest = { showEditProfileDialog = false },
        title = { Text("Edit Employee Details") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = nameInput,
              onValueChange = { nameInput = it },
              label = { Text("Employee Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = emailInput,
              onValueChange = { emailInput = it },
              label = { Text("Employee Email") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              viewModel.updateEmployeeProfileDetails(employee.employeeUid, nameInput, emailInput)
              showEditProfileDialog = false
            }
          ) {
            Text("Save")
          }
        },
        dismissButton = {
          TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") }
        },
      )
    }

    if (showRemoveConfirm) {
      AlertDialog(
        onDismissRequest = { showRemoveConfirm = false },
        title = { Text("Remove ${employee.name}?") },
        text = {
          Text(
            "Removing access immediately revokes all spreadsheet assignments for this employee and marks their account as REMOVED."
          )
        },
        confirmButton = {
          Button(
            onClick = {
              viewModel.removeEmployee(employee)
              showRemoveConfirm = false
              onBack()
            },
            colors = ButtonDefaults.buttonColors(containerColor = extended.error),
          ) {
            Text("Remove Access")
          }
        },
        dismissButton = {
          TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
        },
      )
    }

    val status = employee.resolvedStatus()
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              EmployeeAvatar(name = employee.name, photoUrl = employee.photoUrl)
              Column(modifier = Modifier.weight(1f)) {
                Text(employee.name, style = MaterialTheme.typography.titleLarge)
                Text(
                  employee.email,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              StatusPill(
                label = status.name,
                containerColor =
                  if (status == AccountStatus.ACTIVE) extended.healthy.copy(alpha = 0.14f)
                  else extended.error.copy(alpha = 0.14f),
                contentColor =
                  if (status == AccountStatus.ACTIVE) extended.healthy else extended.error,
              )
            }
            Text("Added by: ${employee.addedByAdminName} (${employee.addedByAdminEmail})", style = MaterialTheme.typography.bodySmall)
            Text("Invite Code: ${employee.inviteCode}", style = MaterialTheme.typography.bodySmall)
            Text("Invited on: ${SearchEngine.formatTimestamp(employee.invitedAt?.toDate()?.time)}", style = MaterialTheme.typography.bodySmall)
            Text("Joined on: ${SearchEngine.formatTimestamp(employee.joinedAt?.toDate()?.time)}", style = MaterialTheme.typography.bodySmall)
            Text(
              "Totals: ${employee.totalSearches} searches • ${employee.totalRecordsAdded} records added",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (status == AccountStatus.ACTIVE) {
            OutlinedButton(
              onClick = {
                viewModel.updateEmployeeAccess(
                  employee = employee,
                  newStatus = AccountStatus.SUSPENDED,
                  newDefaultPermission = defaultPerm,
                  newAssignedSheetIds = assignedSheets.toList(),
                  newSheetPermissions = sheetPerms.toMap(),
                )
              },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.size(4.dp))
              Text("Suspend")
            }
          } else {
            Button(
              onClick = {
                viewModel.updateEmployeeAccess(
                  employee = employee,
                  newStatus = AccountStatus.ACTIVE,
                  newDefaultPermission = defaultPerm,
                  newAssignedSheetIds = assignedSheets.toList(),
                  newSheetPermissions = sheetPerms.toMap(),
                )
              },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.size(4.dp))
              Text("Reactivate")
            }
          }

          OutlinedButton(
            onClick = { showRemoveConfirm = true },
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Icon(
              Icons.Default.DeleteOutline,
              contentDescription = null,
              tint = extended.error,
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text("Remove", color = extended.error)
          }
        }
      }

      // Sheet-Level Permission Controls
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text("Sheet Access & Permission Matrix", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
              expanded = permDropdownExpanded,
              onExpandedChange = { permDropdownExpanded = it },
            ) {
              OutlinedTextField(
                value = defaultPerm.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Permission Level") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = permDropdownExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(
                expanded = permDropdownExpanded,
                onDismissRequest = { permDropdownExpanded = false },
              ) {
                SheetPermissionLevel.entries.forEach { lvl ->
                  DropdownMenuItem(
                    text = {
                      Column {
                        Text(lvl.label, style = MaterialTheme.typography.bodyMedium)
                        Text(lvl.description, style = MaterialTheme.typography.bodySmall)
                      }
                    },
                    onClick = {
                      defaultPerm = lvl
                      permDropdownExpanded = false
                    },
                  )
                }
              }
            }

            if (sheets.isEmpty()) {
              Text(
                "No spreadsheets connected yet. Connect spreadsheets first to assign sheet access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              sheets.forEach { sheet ->
                val isAssigned = sheet.id in assignedSheets
                var sheetPermMenu by remember(sheet.id) { mutableStateOf(false) }
                val currentSheetPerm =
                  SheetPermissionLevel.fromRaw(sheetPerms[sheet.id] ?: defaultPerm.name)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Checkbox(
                    checked = isAssigned,
                    onCheckedChange = { checked ->
                      if (checked) {
                        if (sheet.id !in assignedSheets) assignedSheets.add(sheet.id)
                      } else {
                        assignedSheets.remove(sheet.id)
                      }
                    },
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(sheet.customName, style = MaterialTheme.typography.titleSmall)
                    Text(
                      "${sheet.totalRecords} records",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (isAssigned) {
                    ExposedDropdownMenuBox(
                      expanded = sheetPermMenu,
                      onExpandedChange = { sheetPermMenu = it },
                    ) {
                      OutlinedButton(
                        onClick = { sheetPermMenu = true },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                      ) {
                        Text(currentSheetPerm.label, maxLines = 1)
                      }
                      ExposedDropdownMenu(
                        expanded = sheetPermMenu,
                        onDismissRequest = { sheetPermMenu = false },
                      ) {
                        SheetPermissionLevel.entries.forEach { lvl ->
                          DropdownMenuItem(
                            text = { Text(lvl.label) },
                            onClick = {
                              sheetPerms[sheet.id] = lvl.name
                              sheetPermMenu = false
                            },
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            Button(
              onClick = {
                viewModel.updateEmployeeAccess(
                  employee = employee,
                  newStatus = status,
                  newDefaultPermission = defaultPerm,
                  newAssignedSheetIds = assignedSheets.toList(),
                  newSheetPermissions = sheetPerms.toMap(),
                )
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("save_employee_permissions_button"),
            ) {
              Text("Save Permissions & Assignments")
            }
          }
        }
      }

      // Export employee activity CSV
      item {
        OutlinedButton(
          onClick = {
            val csv = buildString {
              appendLine("Timestamp,Activity Type,Description,Sheet,Query")
              empLogs.forEach { log ->
                val ts = SearchEngine.formatTimestamp(log.createdAt?.toDate()?.time)
                appendLine(
                  "\"$ts\",\"${log.activityType}\",\"${log.description.replace("\"", "\"\"")}\",\"${log.sheetName.orEmpty()}\",\"${log.searchQuery.orEmpty()}\""
                )
              }
            }
            val intent =
              Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Employee Report - ${employee.name}")
                putExtra(Intent.EXTRA_TEXT, csv)
              }
            context.startActivity(Intent.createChooser(intent, "Export Employee Activity CSV"))
          },
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.size(6.dp))
          Text("Export Employee Activity Report (CSV)")
        }
      }

      item {
        Text(text = "Records Added (${empAudits.size})", style = MaterialTheme.typography.titleMedium)
      }
      items(empAudits.take(10), key = { it.id }) { audit ->
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${audit.spreadsheetName} / ${audit.worksheetTitle} • Row #${audit.rowNumber}", style = MaterialTheme.typography.titleSmall)
            Text(audit.recordSummary, style = MaterialTheme.typography.bodySmall)
            Text(
              SearchEngine.formatTimestamp(audit.createdAt?.toDate()?.time),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      item {
        Text(text = "Recent Employee Activities (${empLogs.size})", style = MaterialTheme.typography.titleMedium)
      }
      items(empLogs.take(20), key = { it.id }) { log ->
        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(log.activityType.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
              Text(SearchEngine.formatRelativeTime(log.createdAt?.toDate()?.time), style = MaterialTheme.typography.labelSmall)
            }
            Text(log.description, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

@Composable
fun PermissionsMatrixScreen(
  viewModel: MainWorkspaceViewModel,
  onBack: () -> Unit,
  onOpenEmployeeDetail: (String) -> Unit,
) {
  val employeesUi by viewModel.employeesState.collectAsStateWithLifecycle()
  val sheetsUi by viewModel.sheetsState.collectAsStateWithLifecycle()
  val employees = (employeesUi as? UiState.Success)?.data.orEmpty()
  val sheets = (sheetsUi as? UiState.Success)?.data.orEmpty()

  Scaffold(
    topBar = {
      @OptIn(ExperimentalMaterial3Api::class)
      TopAppBar(
        title = { Text("Permissions Matrix") },
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
      if (employees.isEmpty() || sheets.isEmpty()) {
        item {
          EmptyStateCard(
            title = "Matrix Not Ready",
            message = "Connect at least one Google Sheet and register at least one employee to configure per-sheet permissions.",
            showIllustration = false,
          )
        }
      } else {
        items(employees, key = { it.employeeUid }) { emp ->
          Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpenEmployeeDetail(emp.employeeUid) },
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
                  Text(emp.name, style = MaterialTheme.typography.titleMedium)
                  Text(emp.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Edit →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
              }
              sheets.forEach { sheet ->
                val assigned = sheet.id in emp.assignedSheetIds
                val perm = if (assigned) emp.permissionForSheet(sheet.id).label else "No Access"
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(sheet.customName, style = MaterialTheme.typography.bodySmall)
                  Text(
                    perm,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (assigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInviteDialog(
  sheets: List<SheetConnection>,
  submitError: String? = null,
  onCreateInvite: (String, SheetPermissionLevel, List<String>, Map<String, String>, Long, Int?) -> Unit,
  onDismiss: () -> Unit,
) {
  var targetEmail by remember { mutableStateOf("") }
  var emailTouched by remember { mutableStateOf(false) }
  var selectedPerm by remember { mutableStateOf(SheetPermissionLevel.SEARCH_ADD) }
  var permExpanded by remember { mutableStateOf(false) }
  var maxUsesText by remember { mutableStateOf("1") }
  var expiryDaysText by remember { mutableStateOf("7") }
  val selectedSheetIds = remember(sheets) {
    mutableStateListOf<String>().apply {
      addAll(sheets.filter { it.active }.map { it.id })
    }
  }
  val sheetPerms = remember(sheets) {
    mutableStateMapOf<String, String>().apply {
      sheets.filter { it.active }.forEach { put(it.id, SheetPermissionLevel.SEARCH_ADD.name) }
    }
  }

  val normalizedEmail = remember(targetEmail) { InvitationValidator.normalizeEmail(targetEmail) }
  val emailError = remember(targetEmail) { InvitationValidator.validateEmailOrError(targetEmail) }
  val hasValidSheetSelection = sheets.isEmpty() || selectedSheetIds.isNotEmpty()
  val canSubmit = emailError == null && hasValidSheetSelection

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Invite Employee (Email Required)") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = targetEmail,
          onValueChange = {
            targetEmail = it
            emailTouched = true
          },
          label = { Text("Employee Email *") },
          placeholder = { Text("employee@company.com") },
          singleLine = true,
          isError = emailTouched && emailError != null,
          supportingText = {
            if (emailTouched && emailError != null) {
              Text(emailError, color = MaterialTheme.colorScheme.error)
            } else {
              Text(
                "Normalized to lowercase. Signing in with this Google email auto-assigns EMPLOYEE access.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("invite_email_input"),
        )

        if (!submitError.isNullOrBlank()) {
          Text(
            text = submitError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("invite_submit_error"),
          )
        }

        ExposedDropdownMenuBox(expanded = permExpanded, onExpandedChange = { permExpanded = it }) {
          OutlinedTextField(
            value = selectedPerm.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Default Permission (Role: EMPLOYEE)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = permExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = permExpanded, onDismissRequest = { permExpanded = false }) {
            SheetPermissionLevel.entries.forEach { lvl ->
              DropdownMenuItem(
                text = { Text(lvl.label) },
                onClick = {
                  selectedPerm = lvl
                  permExpanded = false
                },
              )
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = maxUsesText,
            onValueChange = { maxUsesText = it.filter { c -> c.isDigit() } },
            label = { Text("Max Uses") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = expiryDaysText,
            onValueChange = { expiryDaysText = it.filter { c -> c.isDigit() } },
            label = { Text("Expires (Days)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        if (sheets.isNotEmpty()) {
          Text("Assign Spreadsheets & Permissions:", style = MaterialTheme.typography.titleSmall)
          if (selectedSheetIds.isEmpty()) {
            Text(
              "Select at least one spreadsheet to grant access.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          sheets.forEach { sheet ->
            val checked = sheet.id in selectedSheetIds
            var perSheetMenu by remember(sheet.id) { mutableStateOf(false) }
            val currentLevel =
              SheetPermissionLevel.fromRaw(sheetPerms[sheet.id] ?: selectedPerm.name)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = { c ->
                  if (c) {
                    if (sheet.id !in selectedSheetIds) selectedSheetIds.add(sheet.id)
                    sheetPerms.putIfAbsent(sheet.id, selectedPerm.name)
                  } else {
                    selectedSheetIds.remove(sheet.id)
                  }
                },
                modifier = Modifier.testTag("invite_sheet_checkbox_${sheet.id}"),
              )
              Text(
                text = sheet.customName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              if (checked) {
                ExposedDropdownMenuBox(
                  expanded = perSheetMenu,
                  onExpandedChange = { perSheetMenu = it },
                ) {
                  OutlinedButton(
                    onClick = { perSheetMenu = true },
                    modifier =
                      Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("invite_sheet_perm_${sheet.id}"),
                  ) {
                    Text(currentLevel.label, maxLines = 1)
                  }
                  ExposedDropdownMenu(
                    expanded = perSheetMenu,
                    onDismissRequest = { perSheetMenu = false },
                  ) {
                    SheetPermissionLevel.entries.forEach { lvl ->
                      DropdownMenuItem(
                        text = { Text(lvl.label) },
                        onClick = {
                          sheetPerms[sheet.id] = lvl.name
                          perSheetMenu = false
                        },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        enabled = canSubmit,
        onClick = {
          emailTouched = true
          if (canSubmit) {
            val permsToSend =
              selectedSheetIds.associateWith { sid ->
                sheetPerms[sid] ?: selectedPerm.name
              }
            onCreateInvite(
              normalizedEmail,
              selectedPerm,
              selectedSheetIds.toList(),
              permsToSend,
              maxUsesText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L,
              expiryDaysText.toIntOrNull()?.coerceAtLeast(1),
            )
          }
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("send_invitation_button"),
      ) {
        Text("Send Invitation")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
fun InviteDispatchDialog(
  invitation: Invitation,
  onSendEmail: () -> Unit,
  onShareLink: () -> Unit,
  onCopyLink: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val email = invitation.normalizedTargetEmail().ifBlank { "employee" }
  val inviteUrl = remember(invitation.inviteCode) {
    AppLinksConfig.buildInviteUrl(context, invitation.inviteCode)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Invitation Ready") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Created EMPLOYEE access for $email (${invitation.assignedSheetIds.size} sheet${if (invitation.assignedSheetIds.size == 1) "" else "s"}).",
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = "The employee can simply sign in with Google using $email — or tap this link to open their Employee Dashboard automatically:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = inviteUrl,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Code: ${invitation.inviteCode}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Button(
          onClick = onSendEmail,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("dispatch_send_email_button"),
        ) {
          Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(8.dp))
          Text("Send Email to $email", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onCopyLink,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("dispatch_copy_button"),
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Copy Link")
          }
          OutlinedButton(
            onClick = onShareLink,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("dispatch_share_button"),
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Share")
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("dispatch_done_button"),
      ) {
        Text("Done")
      }
    },
  )
}

@Composable
fun EmployeeAvatar(
  name: String,
  photoUrl: String?,
  modifier: Modifier = Modifier,
) {
  if (!photoUrl.isNullOrBlank()) {
    AsyncImage(
      model = photoUrl,
      contentDescription = "$name avatar",
      modifier = modifier.size(44.dp).clip(CircleShape),
      contentScale = ContentScale.Crop,
    )
  } else {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "E"
    Box(
      modifier =
        modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = initial,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
