package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.util.AppLinksConfig
import kotlinx.coroutines.launch

enum class LegalDocumentType(val routeKey: String, val title: String) {
  PRIVACY_POLICY("privacy", "Smart Sheet Privacy Policy"),
  TERMS_OF_SERVICE("terms", "Smart Sheet Terms of Service"),
  HOME_PAGE("home", "Smart Sheet Public Home Page");

  companion object {
    fun fromKey(key: String): LegalDocumentType {
      return entries.firstOrNull { it.routeKey == key } ?: PRIVACY_POLICY
    }
  }
}

private data class LegalSectionBlock(
  val heading: String,
  val body: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
  documentType: LegalDocumentType,
  onBack: () -> Unit,
  onOpenOtherDocument: ((LegalDocumentType) -> Unit)? = null,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val links = remember(context) { AppLinksConfig.resolve(context) }
  val developerName = remember(context) { AppLinksConfig.developerContactName(context) }
  val developerEmail = remember(context) { AppLinksConfig.developerContactEmail(context) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val url =
    when (documentType) {
      LegalDocumentType.PRIVACY_POLICY -> links.privacyPolicyUrl
      LegalDocumentType.TERMS_OF_SERVICE -> links.termsOfServiceUrl
      LegalDocumentType.HOME_PAGE -> links.homePageUrl
    }

  val icon =
    when (documentType) {
      LegalDocumentType.PRIVACY_POLICY -> Icons.Default.Policy
      LegalDocumentType.TERMS_OF_SERVICE -> Icons.Default.Description
      LegalDocumentType.HOME_PAGE -> Icons.Default.Public
    }

  val sections =
    remember(documentType, developerName, developerEmail, links) {
      buildSectionsFor(documentType, developerName, developerEmail, links.accountDeletionUrl)
    }

  val triggerCopy: () -> Unit = {
    if (AppLinksConfig.copyLinkToClipboard(context, documentType.title, url)) {
      scope.launch { snackbarHostState.showSnackbar("Link copied successfully") }
    } else {
      scope.launch { snackbarHostState.showSnackbar("Unable to copy link") }
    }
  }

  val triggerShare: () -> Unit = {
    val launched = AppLinksConfig.shareLink(context, documentType.title, url)
    if (!launched) {
      scope.launch { snackbarHostState.showSnackbar("Link copied successfully") }
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(documentType.title, maxLines = 1) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("legal_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = triggerShare,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("legal_topbar_share_button"),
          ) {
            Icon(Icons.Default.Share, contentDescription = "Share link")
          }
        },
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column(modifier = Modifier.weight(1f)) {
                Text(documentType.title, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Effective: Nov 22, 2026 • Last Updated: Nov 22, 2026",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surface,
            ) {
              Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = { AppLinksConfig.openExternalUrl(context, url) },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("legal_open_browser_button"),
              ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Open Web")
              }
              OutlinedButton(
                onClick = triggerCopy,
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("legal_viewer_copy_button"),
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Copy")
              }
              OutlinedButton(
                onClick = triggerShare,
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("legal_viewer_share_button"),
              ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Share")
              }
            }
          }
        }
      }

      if (onOpenOtherDocument != null && documentType == LegalDocumentType.HOME_PAGE) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onOpenOtherDocument(LegalDocumentType.PRIVACY_POLICY) },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Text("Privacy Policy")
            }
            OutlinedButton(
              onClick = { onOpenOtherDocument(LegalDocumentType.TERMS_OF_SERVICE) },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Text("Terms of Service")
            }
          }
        }
      }

      sections.forEach { sec ->
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(sec.heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
              Text(sec.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
          }
        }
      }
    }
  }
}

private fun buildSectionsFor(
  type: LegalDocumentType,
  developerName: String,
  developerEmail: String,
  accountDeletionUrl: String,
): List<LegalSectionBlock> =
  when (type) {
    LegalDocumentType.PRIVACY_POLICY ->
      listOf(
        LegalSectionBlock(
          "1. Information Collected",
          "• Google Sign-In Profile: Firebase user ID, display name, email address, and profile photo URL received via Android Credential Manager and Firebase Authentication.\n" +
            "• Workspace Metadata: Connected spreadsheet IDs, custom sheet display names, worksheet configurations, employee invitation codes, assigned sheet permissions, saved searches, sync logs, record audits, and activity logs stored in Cloud Firestore.\n" +
            "• Cached Spreadsheet Rows: Headers and row values from connected Google Sheets are cached locally in an on-device Room database for fast search.\n" +
            "• Camera (Optional IMEI Barcode Scan): Camera frames are processed locally on-device with ML Kit Barcode Scanning and are never uploaded or stored.",
        ),
        LegalSectionBlock(
          "2. Google Account Authentication",
          "Sign-in uses Jetpack Credential Manager with Google Sign-In and Firebase Authentication. Smart Sheet never sees or stores your Google password.",
        ),
        LegalSectionBlock(
          "3. Google Sheets Access & Requested Permissions",
          "Each user authorizes their own Google Account via OAuth 2.0 requesting only:\n" +
            "• https://www.googleapis.com/auth/drive.file — List, select, and create only spreadsheets authorized or created via Smart Sheet.\n" +
            "• https://www.googleapis.com/auth/spreadsheets — Read, append, update, and delete rows in connected Google Sheets.",
        ),
        LegalSectionBlock(
          "4. Processing, Storage & Security",
          "• Smart Sheet communicates over HTTPS/TLS directly with Google Drive v3, Google Sheets v4, and Firebase APIs.\n" +
            "• Firestore security rules enforce strict workspace and role isolation.\n" +
            "• OAuth access tokens are stored in memory per signed-in user session, redacted from logs, and revoked on disconnect.\n" +
            "• Sensitive Aadhaar values are masked by default (XXXX-XXXX-last4) unless explicitly revealed.",
        ),
        LegalSectionBlock(
          "5. Third-Party Sharing & Google Limited Use",
          "Smart Sheet does not sell personal information, display third-party ads, or use Google Workspace data to train AI/ML models. Information received from Google APIs adheres to the Google API Services User Data Policy, including Limited Use requirements.",
        ),
        LegalSectionBlock(
          "6. Data Retention & Account Deletion",
          "You can revoke Google Drive/Sheets OAuth at any time from the Sheets tab ('Disconnect Google') or at myaccount.google.com/permissions. You can delete your account directly from My Account → Account Management → Delete Account, or via $accountDeletionUrl. Deleting your Smart Sheet account never deletes your spreadsheets in Google Drive.",
        ),
        LegalSectionBlock(
          "7. Privacy Rights & Developer Contact",
          "You may review, disconnect, or delete your account data at any time.\nDeveloper Contact: $developerName\nEmail: $developerEmail",
        ),
      )

    LegalDocumentType.TERMS_OF_SERVICE ->
      listOf(
        LegalSectionBlock(
          "1. Introduction & Acceptance",
          "By signing into or using Smart Sheet (com.aistudio.smartsheet.kxmpzq), you agree to these Terms of Service and the Smart Sheet Privacy Policy.",
        ),
        LegalSectionBlock(
          "2. Description of Smart Sheet Services",
          "Smart Sheet provides multi-sheet search, record creation, on-device IMEI barcode scanning, Google Drive spreadsheet connection, and Admin/Employee workspace permission management.",
        ),
        LegalSectionBlock(
          "3. Account & Google Connection Responsibilities",
          "You are responsible for safeguarding your device and Google Account. Each user connects their own Google Account via OAuth 2.0 and must only access spreadsheets they are authorized to view or modify.",
        ),
        LegalSectionBlock(
          "4. Google Sheets Permissions & Acceptable Use",
          "Smart Sheet requests drive.file and spreadsheets scopes strictly for user-authorized sheets. You agree not to bypass workspace permissions, abuse API quotas, or process unlawful content.",
        ),
        LegalSectionBlock(
          "5. User Data Ownership",
          "You retain 100% ownership of all spreadsheets and files stored in your Google Drive. Google Sheets remains the primary source of truth.",
        ),
        LegalSectionBlock(
          "6. Availability, Third-Party APIs & Liability",
          "Smart Sheet relies on Firebase and Google Drive/Sheets APIs and is provided 'as is' without guaranteed uptime SLA. Maintain backups of critical spreadsheets in Google Drive.",
        ),
        LegalSectionBlock(
          "7. Suspension, Termination & Account Deletion",
          "Workspace Admins may suspend or remove employee access at any time. Users may delete their own account in My Account → Account Management → Delete Account or via $accountDeletionUrl.",
        ),
        LegalSectionBlock(
          "8. Changes & Contact",
          "Updated terms apply upon publication. Contact: $developerName ($developerEmail).",
        ),
      )

    LegalDocumentType.HOME_PAGE ->
      listOf(
        LegalSectionBlock(
          "Smart Sheet — Google Sheets Workspace Platform",
          "Connect multiple Google Sheets, search customer/IMEI/Aadhaar records in milliseconds, scan IMEI barcodes on-device, and manage Employee permissions under a secure Admin workspace.",
        ),
        LegalSectionBlock(
          "Public Access & Direct Sharing",
          "Share the public Home Page link via WhatsApp, email, or any app so teammates and reviewers can inspect Smart Sheet's overview, Privacy Policy, Terms of Service, and Account Deletion guide without logging in.",
        ),
        LegalSectionBlock(
          "Developer Support",
          "Contact: $developerName ($developerEmail)",
        ),
      )
  }
