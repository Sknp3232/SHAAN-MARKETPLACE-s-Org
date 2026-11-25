package com.example.data.remote

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object GoogleOAuthErrorMapper {
  fun mapStatusOrMessage(
    statusCode: Int? = null,
    rawMessage: String? = null,
    resultCode: Int? = null,
  ): String {
    val msg = rawMessage.orEmpty()
    val lower = msg.lowercase()

    if (resultCode == Activity.RESULT_CANCELED && statusCode == null && msg.isBlank()) {
      return "Google authorization was cancelled or blocked before completion. Verify your account or OAuth consent screen status."
    }

    return when {
      lower.contains("not completed the google verification") ||
        lower.contains("unverified") ||
        lower.contains("org_internal") ->
        "OAuth Access Blocked: This app is currently awaiting Google OAuth verification or is restricted to test users. Ask the administrator to publish the OAuth consent screen or add your Google account under Test Users."

      lower.contains("access_denied") || statusCode == CommonStatusCodes.CANCELED ->
        "Access Denied: Google Drive & Sheets permission was declined or blocked by account policy."

      lower.contains("redirect_uri_mismatch") ->
        "OAuth Configuration Error (Redirect URI Mismatch): Check the authorized redirect URI / Android OAuth client configuration in Google Cloud Console."

      lower.contains("invalid_client") ||
        statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
        "OAuth Configuration Error (Invalid Client / SHA-1): Verify the Android OAuth Client ID, package name, and signing SHA-1 certificate fingerprint in Google Cloud Console."

      lower.contains("invalid_grant") ||
        lower.contains("token has been expired or revoked") ||
        statusCode == CommonStatusCodes.SIGN_IN_REQUIRED ->
        "Google OAuth session expired or was revoked. Please tap Reconnect Google to authorize again."

      lower.contains("quota") ||
        lower.contains("ratelimit") ||
        lower.contains("rate limit") ->
        "Google API quota exceeded. Please wait a moment and try again."

      lower.contains("accessnotconfigured") ||
        lower.contains("has not been used in project") ||
        lower.contains("insufficient") ->
        "Missing Google API Permissions: Ensure Google Drive API and Google Sheets API are enabled and requested scopes were granted."

      statusCode == CommonStatusCodes.NETWORK_ERROR ->
        "Network error while contacting Google Authorization servers. Check your internet connection."

      statusCode == CommonStatusCodes.INVALID_ACCOUNT ->
        "The selected Google account could not be verified. Please sign out and sign in again."

      msg.isNotBlank() ->
        "Google authorization failed: ${sanitizeErrorMessage(msg)}"

      else ->
        "Google Drive & Sheets authorization failed. Please try reconnecting your Google Account."
    }
  }

  fun sanitizeErrorMessage(raw: String): String {
    // Ensure bearer/access tokens or long opaque tokens are never surfaced in UI or logs
    return raw
      .replace(Regex("ya29\\.[A-Za-z0-9\\-_\\.]+"), "[REDACTED_TOKEN]")
      .replace(Regex("Bearer\\s+[A-Za-z0-9\\-_\\.]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
  }
}

sealed interface SilentRefreshOutcome {
  data class TokenReady(val accessToken: String, val connectedEmail: String?) : SilentRefreshOutcome
  data class NeedsUserConsent(val intentSender: IntentSender?) : SilentRefreshOutcome
  data class Failed(val userMessage: String) : SilentRefreshOutcome
}

class GoogleWorkspaceAuthManager {
  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  private val _accessToken = MutableStateFlow<String?>(null)
  val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

  private val _isAuthorized = MutableStateFlow(false)
  val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

  private val _connectedEmail = MutableStateFlow<String?>(null)
  val connectedEmail: StateFlow<String?> = _connectedEmail.asStateFlow()

  private val _lastAuthError = MutableStateFlow<String?>(null)
  val lastAuthError: StateFlow<String?> = _lastAuthError.asStateFlow()

  fun setToken(token: String?, email: String? = null) {
    _accessToken.value = token
    _isAuthorized.value = !token.isNullOrBlank()
    if (!email.isNullOrBlank()) {
      _connectedEmail.value = email
    }
    if (!token.isNullOrBlank()) {
      _lastAuthError.value = null
    }
  }

  fun clearToken(clearError: Boolean = true) {
    _accessToken.value = null
    _isAuthorized.value = false
    _connectedEmail.value = null
    if (clearError) {
      _lastAuthError.value = null
    }
  }

  fun clearAuthError() {
    _lastAuthError.value = null
  }

  private fun buildAuthRequest(expectedEmail: String?): AuthorizationRequest {
    val builder = AuthorizationRequest.builder().setRequestedScopes(REQUIRED_SCOPES)
    if (!expectedEmail.isNullOrBlank() && expectedEmail.contains("@")) {
      builder.setAccount(Account(expectedEmail.trim(), "com.google"))
    }
    return builder.build()
  }

  fun requestWorkspaceAuthorization(
    activity: Activity,
    expectedEmail: String?,
    onResolutionRequired: (IntentSender) -> Unit,
    onAuthorized: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    _lastAuthError.value = null
    authorizeInternal(
      activity = activity,
      expectedEmail = expectedEmail,
      allowAccountFallback = true,
      onResolutionRequired = onResolutionRequired,
      onAuthorized = onAuthorized,
      onError = onError,
    )
  }

  private fun authorizeInternal(
    activity: Activity,
    expectedEmail: String?,
    allowAccountFallback: Boolean,
    onResolutionRequired: (IntentSender) -> Unit,
    onAuthorized: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    val request = buildAuthRequest(expectedEmail)

    Identity.getAuthorizationClient(activity)
      .authorize(request)
      .addOnSuccessListener { result ->
        if (result.hasResolution()) {
          val pendingIntent = result.pendingIntent
          if (pendingIntent != null) {
            onResolutionRequired(pendingIntent.intentSender)
          } else {
            val err = "Google authorization consent screen could not be launched."
            _lastAuthError.value = err
            onError(err)
          }
        } else {
          val token = result.accessToken
          val granted = result.grantedScopes
          if (!token.isNullOrBlank() && hasAllRequiredScopes(granted)) {
            setToken(token, expectedEmail)
            onAuthorized(token)
          } else if (!token.isNullOrBlank()) {
            val err =
              "Missing Google API Permissions: Please grant both Google Drive (drive.file) and Google Sheets (spreadsheets) access."
            clearToken(clearError = false)
            _lastAuthError.value = err
            onError(err)
          } else {
            val err = "Google Authorization completed without returning an access token."
            _lastAuthError.value = err
            onError(err)
          }
        }
      }
      .addOnFailureListener { e ->
        val apiCode = (e as? ApiException)?.statusCode
        if (apiCode == CommonStatusCodes.INVALID_ACCOUNT &&
          allowAccountFallback &&
          !expectedEmail.isNullOrBlank()
        ) {
          authorizeInternal(
            activity = activity,
            expectedEmail = null,
            allowAccountFallback = false,
            onResolutionRequired = onResolutionRequired,
            onAuthorized = onAuthorized,
            onError = onError,
          )
          return@addOnFailureListener
        }
        val mapped = GoogleOAuthErrorMapper.mapStatusOrMessage(apiCode, e.localizedMessage)
        Log.e(TAG, "Google Workspace authorization failed (code=$apiCode): $mapped")
        clearToken(clearError = false)
        _lastAuthError.value = mapped
        onError(mapped)
      }
  }

  fun handleAuthorizationResult(
    activity: Activity,
    resultCode: Int,
    data: Intent?,
    expectedEmail: String?,
    onAuthorized: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (resultCode == Activity.RESULT_CANCELED && data == null) {
      val mapped =
        GoogleOAuthErrorMapper.mapStatusOrMessage(
          statusCode = CommonStatusCodes.CANCELED,
          rawMessage = null,
          resultCode = resultCode,
        )
      Log.w(TAG, "Google Workspace authorization cancelled by user or blocked.")
      _lastAuthError.value = mapped
      onError(mapped)
      return
    }

    try {
      val result =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
      val token = result.accessToken
      val granted = result.grantedScopes
      if (!token.isNullOrBlank() && hasAllRequiredScopes(granted)) {
        setToken(token, expectedEmail)
        onAuthorized(token)
      } else if (!token.isNullOrBlank()) {
        val err =
          "Missing Google API Permissions: Both Google Drive (drive.file) and Google Sheets (spreadsheets) permissions are required."
        clearToken(clearError = false)
        _lastAuthError.value = err
        onError(err)
      } else {
        val err = "Authorization completed without returning an access token."
        _lastAuthError.value = err
        onError(err)
      }
    } catch (e: ApiException) {
      val mapped =
        GoogleOAuthErrorMapper.mapStatusOrMessage(
          statusCode = e.statusCode,
          rawMessage = e.localizedMessage,
          resultCode = resultCode,
        )
      Log.w(TAG, "Google Workspace consent failed (status=${e.statusCode}): $mapped")
      clearToken(clearError = false)
      _lastAuthError.value = mapped
      onError(mapped)
    } catch (e: Exception) {
      val mapped =
        GoogleOAuthErrorMapper.mapStatusOrMessage(
          rawMessage = e.localizedMessage,
          resultCode = resultCode,
        )
      Log.w(TAG, "Google Workspace consent failed: $mapped")
      clearToken(clearError = false)
      _lastAuthError.value = mapped
      onError(mapped)
    }
  }


  suspend fun clearCachedToken(activity: Activity, staleToken: String?) {
    if (staleToken.isNullOrBlank()) return
    try {
      withContext(Dispatchers.IO) {
        GoogleAuthUtil.clearToken(activity, staleToken)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Unable to clear cached GMS token: ${GoogleOAuthErrorMapper.sanitizeErrorMessage(e.message.orEmpty())}")
    } finally {
      _accessToken.value = null
      _isAuthorized.value = false
    }
  }

  suspend fun refreshAccessToken(
    activity: Activity,
    expectedEmail: String?,
    staleTokenToClear: String? = null,
  ): SilentRefreshOutcome {
    if (!staleTokenToClear.isNullOrBlank()) {
      clearCachedToken(activity, staleTokenToClear)
    }

    val outcome =
      suspendCancellableCoroutine<SilentRefreshOutcome> { cont ->
        val request = buildAuthRequest(expectedEmail)
        Identity.getAuthorizationClient(activity)
          .authorize(request)
          .addOnSuccessListener { result ->
            if (!cont.isActive) return@addOnSuccessListener
            if (result.hasResolution()) {
              clearToken(clearError = false)
              cont.resume(SilentRefreshOutcome.NeedsUserConsent(result.pendingIntent?.intentSender))
            } else {
              val token = result.accessToken
              val granted = result.grantedScopes
              if (!token.isNullOrBlank() && hasAllRequiredScopes(granted)) {
                setToken(token, expectedEmail)
                cont.resume(SilentRefreshOutcome.TokenReady(token, _connectedEmail.value))
              } else if (!token.isNullOrBlank()) {
                clearToken(clearError = false)
                cont.resume(SilentRefreshOutcome.NeedsUserConsent(null))
              } else {
                cont.resume(
                  SilentRefreshOutcome.Failed("Google Authorization did not return an access token.")
                )
              }
            }
          }
          .addOnFailureListener { e ->
            if (!cont.isActive) return@addOnFailureListener
            val apiCode = (e as? ApiException)?.statusCode
            val msg = GoogleOAuthErrorMapper.mapStatusOrMessage(apiCode, e.localizedMessage)
            clearToken(clearError = false)
            _lastAuthError.value = msg
            cont.resume(SilentRefreshOutcome.Failed(msg))
          }
      }

    return outcome
  }

  suspend fun revokeGoogleAccess(activity: Activity, @Suppress("UNUSED_PARAMETER") accountEmail: String?): Result<Unit> {
    val currentToken = _accessToken.value
    return try {
      if (!currentToken.isNullOrBlank()) {
        withContext(Dispatchers.IO) {
          try {
            val formBody = FormBody.Builder().add("token", currentToken).build()
            val req =
              Request.Builder()
                .url("https://oauth2.googleapis.com/revoke")
                .post(formBody)
                .build()
            httpClient.newCall(req).execute().close()
          } catch (e: Exception) {
            Log.w(
              TAG,
              "OAuth token revocation HTTP notice: ${GoogleOAuthErrorMapper.sanitizeErrorMessage(e.message.orEmpty())}",
            )
          }
        }
        clearCachedToken(activity, currentToken)
      }
      clearToken()
      Result.success(Unit)
    } catch (e: Exception) {
      clearToken()
      Result.failure(e)
    }
  }

  companion object {
    private const val TAG = "WorkspaceOAuth"
    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"

    val REQUIRED_SCOPES: List<Scope> =
      listOf(
        Scope(SCOPE_DRIVE_FILE),
        Scope(SCOPE_SHEETS),
      )

    val SCOPE_DESCRIPTIONS: List<Pair<String, String>> =
      listOf(
        "drive.file" to "Create, view, and edit only spreadsheets authorized or created by Smart Sheet.",
        "spreadsheets" to "Read, append, update, and delete rows in connected Google Sheets.",
      )

    fun hasAllRequiredScopes(grantedScopes: Collection<String>?): Boolean {
      if (grantedScopes.isNullOrEmpty()) return true
      return grantedScopes.contains(SCOPE_DRIVE_FILE) && grantedScopes.contains(SCOPE_SHEETS)
    }
  }
}
