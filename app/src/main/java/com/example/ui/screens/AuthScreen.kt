package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalSmartSheetColors

@Composable
fun AuthScreen(
  inviteCodeInput: String,
  onInviteCodeChange: (String) -> Unit,
  isSigningIn: Boolean,
  authError: String?,
  isBlockedState: Boolean = false,
  onSignInClicked: () -> Unit,
  onRetryClicked: () -> Unit,
  onSignOutClicked: () -> Unit,
  onOpenLegalDocument: (LegalDocumentType) -> Unit = {},
) {
  val extended = LocalSmartSheetColors.current
  var showInviteField by remember(inviteCodeInput) { mutableStateOf(inviteCodeInput.isNotBlank()) }

  Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(176.dp)
              .clip(RoundedCornerShape(22.dp)),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_hero_workspace),
            contentDescription = "Smart Sheet Manager Workspace",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
          Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(14.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Default.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text(
                  text = "Smart Sheet Manager",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Enterprise Google Sheets Platform",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        if (isBlockedState) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = "Access notice",
                  tint = MaterialTheme.colorScheme.error,
                )
                Text(
                  text = "Workspace Access Notice",
                  style = MaterialTheme.typography.titleLarge,
                )
              }
              Text(
                text = authError ?: "Unable to verify your workspace access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                  onClick = onRetryClicked,
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.size(6.dp))
                  Text("Retry")
                }
                OutlinedButton(
                  onClick = onSignOutClicked,
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                ) {
                  Text("Switch Account")
                }
              }
            }
          }
        } else {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = if (inviteCodeInput.isNotBlank()) "Employee Invitation Sign-In" else "Sign In to Workspace",
                  style = MaterialTheme.typography.headlineMedium,
                )
                StatusPill(
                  label = if (inviteCodeInput.isNotBlank()) "EMPLOYEE INVITE" else "ADMIN READY",
                  containerColor =
                    if (inviteCodeInput.isNotBlank()) {
                      extended.healthy.copy(alpha = 0.14f)
                    } else {
                      MaterialTheme.colorScheme.primaryContainer
                    },
                  contentColor =
                    if (inviteCodeInput.isNotBlank()) extended.healthy else MaterialTheme.colorScheme.primary,
                )
              }

              // Clear Role Assignment Rule explanation
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.AdminPanelSettings,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Direct Google Login → Automatic Admin Workspace",
                      style = MaterialTheme.typography.labelLarge,
                    )
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.Badge,
                      contentDescription = null,
                      tint = extended.healthy,
                      modifier = Modifier.size(18.dp),
                    )
                    Text(
                      text = "Invited Employee Gmail or Invite Link → Employee Dashboard",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              TextButton(
                onClick = { showInviteField = !showInviteField },
                modifier = Modifier.testTag("toggle_invite_code_button"),
              ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                  if (showInviteField) "Hide Optional Invitation Code"
                  else "Have an Invitation Code? (Optional)"
                )
              }

              AnimatedVisibility(visible = showInviteField) {
                OutlinedTextField(
                  value = inviteCodeInput,
                  onValueChange = onInviteCodeChange,
                  label = { Text("Employee Invitation Code (Optional)") },
                  placeholder = { Text("e.g. SM-A1B2C3D4") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("invite_code_input"),
                  supportingText = {
                    Text(
                      if (inviteCodeInput.isBlank()) {
                        "No code needed for email invites — just tap Sign in with Google below."
                      } else {
                        "You will join the inviting Admin's workspace as an Employee."
                      }
                    )
                  },
                )
              }

              if (!authError.isNullOrBlank()) {
                Surface(
                  color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    text = authError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                  )
                }
              }

              Button(
                onClick = onSignInClicked,
                enabled = !isSigningIn,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
              ) {
                if (isSigningIn) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                } else {
                  Text(
                    text =
                      if (inviteCodeInput.isNotBlank()) "Accept Invite & Sign in with Google"
                      else "Sign in with Google",
                    style = MaterialTheme.typography.titleMedium,
                  )
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = { onOpenLegalDocument(LegalDocumentType.HOME_PAGE) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("auth_home_page_link"),
          ) {
            Text("Home Page", style = MaterialTheme.typography.labelMedium)
          }
          Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
          TextButton(
            onClick = { onOpenLegalDocument(LegalDocumentType.PRIVACY_POLICY) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("auth_privacy_policy_link"),
          ) {
            Text("Privacy Policy", style = MaterialTheme.typography.labelMedium)
          }
          Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
          TextButton(
            onClick = { onOpenLegalDocument(LegalDocumentType.TERMS_OF_SERVICE) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("auth_terms_link"),
          ) {
            Text("Terms", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}

@Composable
fun ResolvingProfileScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator()
      Text(
        text = "Preparing your workspace...",
        style = MaterialTheme.typography.titleMedium,
      )
    }
  }
}
