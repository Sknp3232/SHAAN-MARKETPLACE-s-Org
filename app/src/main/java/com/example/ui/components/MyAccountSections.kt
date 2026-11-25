package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.screens.LegalDocumentType
import com.example.util.PublicAppLinks

@Composable
fun LegalImportantLinksSection(
  links: PublicAppLinks,
  onViewDocument: (LegalDocumentType) -> Unit,
  onShareLink: (title: String, url: String) -> Unit,
  onCopyLink: (label: String, url: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth().testTag("legal_important_links_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "LEGAL & IMPORTANT LINKS",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )

      LegalLinkRow(
        title = "Privacy Policy",
        subtitle = links.privacyPolicyUrl,
        icon = Icons.Default.Policy,
        primaryActionLabel = "View",
        primaryActionIcon = Icons.Default.Visibility,
        primaryTag = "legal_privacy_view_button",
        shareTag = "legal_privacy_share_button",
        copyTag = "legal_privacy_copy_button",
        onPrimaryClick = { onViewDocument(LegalDocumentType.PRIVACY_POLICY) },
        onShareClick = { onShareLink("Smart Sheet Privacy Policy", links.privacyPolicyUrl) },
        onCopyClick = { onCopyLink("Privacy Policy", links.privacyPolicyUrl) },
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

      LegalLinkRow(
        title = "Terms of Service",
        subtitle = links.termsOfServiceUrl,
        icon = Icons.Default.Description,
        primaryActionLabel = "View",
        primaryActionIcon = Icons.Default.Visibility,
        primaryTag = "legal_terms_view_button",
        shareTag = "legal_terms_share_button",
        copyTag = "legal_terms_copy_button",
        onPrimaryClick = { onViewDocument(LegalDocumentType.TERMS_OF_SERVICE) },
        onShareClick = { onShareLink("Smart Sheet Terms of Service", links.termsOfServiceUrl) },
        onCopyClick = { onCopyLink("Terms of Service", links.termsOfServiceUrl) },
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

      LegalLinkRow(
        title = "App Home Page",
        subtitle = links.homePageUrl,
        icon = Icons.Default.Public,
        primaryActionLabel = "Open",
        primaryActionIcon = Icons.AutoMirrored.Filled.OpenInNew,
        primaryTag = "legal_home_open_button",
        shareTag = "legal_home_share_button",
        copyTag = "legal_home_copy_button",
        onPrimaryClick = { onViewDocument(LegalDocumentType.HOME_PAGE) },
        onShareClick = { onShareLink("Smart Sheet", links.homePageUrl) },
        onCopyClick = { onCopyLink("App Home Page", links.homePageUrl) },
      )
    }
  }
}

@Composable
private fun LegalLinkRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  primaryActionLabel: String,
  primaryActionIcon: ImageVector,
  primaryTag: String,
  shareTag: String,
  copyTag: String,
  onPrimaryClick: () -> Unit,
  onShareClick: () -> Unit,
  onCopyClick: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FilledTonalButton(
        onClick = onPrimaryClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag(primaryTag),
      ) {
        Icon(primaryActionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(primaryActionLabel, maxLines = 1)
      }
      OutlinedButton(
        onClick = onShareClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag(shareTag),
      ) {
        Icon(Icons.Default.Share, contentDescription = "Share $title", modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text("Share", maxLines = 1)
      }
      OutlinedButton(
        onClick = onCopyClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag(copyTag),
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy $title link", modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text("Copy", maxLines = 1)
      }
    }
  }
}

@Composable
fun AccountManagementSection(
  isDeletingAccount: Boolean,
  accountDeletionUrl: String,
  onSignOut: () -> Unit,
  onRequestDeleteAccount: () -> Unit,
  onOpenDeletionGuide: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth().testTag("account_management_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "ACCOUNT MANAGEMENT",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      OutlinedButton(
        onClick = onSignOut,
        enabled = !isDeletingAccount,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("profile_sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Sign Out")
      }
      OutlinedButton(
        onClick = onRequestDeleteAccount,
        enabled = !isDeletingAccount,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("profile_delete_account_button"),
      ) {
        if (isDeletingAccount) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(Modifier.size(8.dp))
          Text("Deleting Account...")
        } else {
          Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(8.dp))
          Text("Delete Account")
        }
      }
      TextButton(
        onClick = onOpenDeletionGuide,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_account_deletion_guide_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(6.dp))
        Text("Web Account Deletion Info ($accountDeletionUrl)", maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
fun DeleteAccountConfirmDialog(
  isAdmin: Boolean,
  onConfirmDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete Smart Sheet Account?") },
    text = {
      Text(
        if (isAdmin) {
          "This permanently deletes your Smart Sheet Admin profile, workspace settings, invitations, local cache, and Firebase Authentication account. Your Google Spreadsheets inside Google Drive will NOT be deleted."
        } else {
          "This permanently deletes your Smart Sheet profile, saved searches, workspace employee access, local cache, and Firebase Authentication account. Any Google Spreadsheets in Google Drive will NOT be deleted."
        }
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirmDelete,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag("delete_account_confirm_button"),
      ) {
        Text("Delete Permanently")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("delete_account_cancel_button"),
      ) {
        Text("Cancel")
      }
    },
  )
}
