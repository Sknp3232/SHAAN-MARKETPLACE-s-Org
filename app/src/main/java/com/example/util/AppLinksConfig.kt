package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.R
import java.net.URI

data class PublicAppLinks(
  val baseUrl: String,
  val homePageUrl: String,
  val privacyPolicyUrl: String,
  val termsOfServiceUrl: String,
  val accountDeletionUrl: String,
)

object AppLinksConfig {
  fun buildPublicLinks(rawBaseUrl: String): PublicAppLinks {
    val cleanBase = rawBaseUrl.trim().trimEnd('/')
    return PublicAppLinks(
      baseUrl = cleanBase,
      homePageUrl = "$cleanBase/",
      privacyPolicyUrl = "$cleanBase/privacy-policy",
      termsOfServiceUrl = "$cleanBase/terms-of-service",
      accountDeletionUrl = "$cleanBase/account-deletion",
    )
  }

  fun buildInviteUrlFromBase(rawBaseUrl: String, inviteCode: String): String {
    val cleanBase = rawBaseUrl.trim().trimEnd('/')
    val safeCode = sanitizeInviteCode(inviteCode)
    return if (safeCode.isBlank()) "$cleanBase/invite" else "$cleanBase/invite?code=$safeCode"
  }

  fun buildInviteUrl(context: Context, inviteCode: String): String {
    return buildInviteUrlFromBase(context.getString(R.string.public_web_base_url), inviteCode)
  }

  fun sanitizeInviteCode(raw: String?): String {
    return raw.orEmpty().trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")
  }

  fun extractInviteCodeFromUriString(rawUri: String?): String? {
    if (rawUri.isNullOrBlank()) return null
    return try {
      val parsed = URI(rawUri.trim())
      val scheme = parsed.scheme?.lowercase().orEmpty()
      val host = parsed.host?.lowercase().orEmpty()
      val path = parsed.path.orEmpty()
      val isInviteUri =
        (scheme == "smartsheet" && host == "invite") ||
          ((scheme == "https" || scheme == "http") && path.startsWith("/invite"))
      if (!isInviteUri) return null
      val query = parsed.rawQuery ?: return null
      query
        .split("&")
        .asSequence()
        .mapNotNull { pair ->
          val idx = pair.indexOf('=')
          if (idx <= 0) null
          else pair.substring(0, idx) to pair.substring(idx + 1)
        }
        .firstOrNull { it.first.equals("code", ignoreCase = true) }
        ?.second
        ?.let { sanitizeInviteCode(it) }
        ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    }
  }

  fun extractInviteCodeFromUri(uri: Uri?): String? {
    if (uri == null) return null
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path.orEmpty()
    val isInviteUri =
      (scheme == "smartsheet" && host == "invite") ||
        ((scheme == "https" || scheme == "http") && path.startsWith("/invite"))
    if (!isInviteUri) return null
    return sanitizeInviteCode(uri.getQueryParameter("code")).takeIf { it.isNotBlank() }
  }

  fun resolve(context: Context): PublicAppLinks {
    return buildPublicLinks(context.getString(R.string.public_web_base_url))
  }

  fun developerContactName(context: Context): String {
    return context.getString(R.string.developer_contact_name)
  }

  fun developerContactEmail(context: Context): String {
    return context.getString(R.string.developer_contact_email)
  }

  fun copyLinkToClipboard(context: Context, label: String, url: String): Boolean {
    return try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
      clipboard.setPrimaryClip(ClipData.newPlainText(label, url))
      true
    } catch (_: Exception) {
      false
    }
  }

  fun shareLink(context: Context, title: String, url: String): Boolean {
    val shareText = "$title\n$url"
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, shareText)
      }
    val chooser =
      Intent.createChooser(sendIntent, "Share $title").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      if (sendIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(chooser)
        true
      } else {
        copyLinkToClipboard(context, title, url)
        false
      }
    } catch (_: Exception) {
      copyLinkToClipboard(context, title, url)
      false
    }
  }

  fun launchInviteEmail(
    context: Context,
    targetEmail: String,
    adminName: String,
    inviteLink: String,
    inviteCode: String,
    sheetCount: Int,
  ): Boolean {
    val cleanEmail = InvitationValidator.normalizeEmail(targetEmail)
    val subject = "You're invited to Smart Sheet by ${adminName.ifBlank { "your Admin" }}"
    val body =
      buildString {
        appendLine("Hi,")
        appendLine()
        appendLine("${adminName.ifBlank { "Your Admin" }} invited you to join their Smart Sheet workspace as an Employee ($sheetCount assigned spreadsheet${if (sheetCount == 1) "" else "s"}). ")
        appendLine()
        appendLine("Accept Invitation Link:")
        appendLine(inviteLink)
        appendLine()
        appendLine("How to sign in:")
        appendLine("1. Tap the invitation link above (or open the Smart Sheet app directly).")
        appendLine("2. Tap 'Sign in with Google' using $cleanEmail.")
        appendLine("3. Your Employee Dashboard and assigned sheets will open automatically.")
        appendLine()
        appendLine("Fallback invitation code (only if needed): $inviteCode")
      }
    val emailIntent =
      Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${Uri.encode(cleanEmail)}")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      if (emailIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(emailIntent)
        true
      } else {
        val fallbackShare =
          Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
              putExtra(Intent.EXTRA_SUBJECT, subject)
              putExtra(Intent.EXTRA_TEXT, body)
            },
            "Send Invitation Email",
          ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(fallbackShare)
        true
      }
    } catch (_: Exception) {
      copyLinkToClipboard(context, "Smart Sheet Invite Link", inviteLink)
      false
    }
  }

  fun openExternalUrl(context: Context, url: String): Boolean {
    return try {
      val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      true
    } catch (_: Exception) {
      false
    }
  }
}
