package com.example.viewmodel

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalPreferencesStore
import com.example.data.repository.AuthRepository
import com.example.model.Invitation
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthSessionState {
  data object CheckingSession : AuthSessionState
  data object SignedOut : AuthSessionState
  data object ResolvingProfile : AuthSessionState
  data class Authenticated(val profile: UserProfile) : AuthSessionState
  data class Blocked(val reason: String) : AuthSessionState
}

class AuthViewModel(
  private val authRepository: AuthRepository,
  private val preferencesStore: LocalPreferencesStore,
) : ViewModel() {

  val firebaseUser: StateFlow<FirebaseUser?> =
    authRepository.authStateFlow().stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = authRepository.currentUser(),
    )

  private val _sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.CheckingSession)
  val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

  private val _inviteCodeInput = MutableStateFlow("")
  val inviteCodeInput: StateFlow<String> = _inviteCodeInput.asStateFlow()

  private val _invitePreview = MutableStateFlow<Invitation?>(null)
  val invitePreview: StateFlow<Invitation?> = _invitePreview.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private val _isSigningIn = MutableStateFlow(false)
  val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

  private val _isDeletingAccount = MutableStateFlow(false)
  val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

  private val resolveMutex = Mutex()

  val darkThemeOverride: StateFlow<Boolean?> =
    preferencesStore.darkThemePreference.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = null,
    )

  init {
    viewModelScope.launch {
      val savedInvite = preferencesStore.pendingInviteCode.firstOrNull()
      if (!savedInvite.isNullOrBlank() && _inviteCodeInput.value.isBlank()) {
        _inviteCodeInput.value = savedInvite
      }
      authRepository.authStateFlow().collect { user ->
        if (user == null) {
          _sessionState.value = AuthSessionState.SignedOut
        } else {
          resolveUserProfile(user, forceRefresh = false)
        }
      }
    }
  }

  fun setIncomingInviteCode(code: String, resolveIfSignedIn: Boolean = false) {
    val clean = code.trim().uppercase()
    _inviteCodeInput.value = clean
    _authError.value = null
    viewModelScope.launch {
      preferencesStore.setPendingInviteCode(clean.ifBlank { null })
      if (clean.length >= 4) {
        val signedInUser = authRepository.currentUser()
        if (signedInUser != null) {
          if (resolveIfSignedIn) {
            resolveUserProfile(signedInUser, forceRefresh = true)
          } else {
            val res = authRepository.validateInvitationPreview(clean)
            _invitePreview.value = res.getOrNull()
          }
        }
      } else {
        _invitePreview.value = null
      }
    }
  }

  fun attemptAutoSignIn(context: Context, credentialManager: CredentialManager) {
    if (authRepository.currentUser() != null) return
    authRepository.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      scope = viewModelScope,
      onAuthSuccess = {
        // authStateFlow collector automatically invokes resolveUserProfile once
      },
      onUnauthenticated = {
        if (authRepository.currentUser() == null) {
          _sessionState.value = AuthSessionState.SignedOut
        }
      },
    )
  }

  fun signInWithGoogle(context: Context, credentialManager: CredentialManager) {
    _authError.value = null
    _isSigningIn.value = true
    authRepository.onGoogleSignInClicked(
      context = context,
      credentialManager = credentialManager,
      scope = viewModelScope,
      onAuthSuccess = {
        _isSigningIn.value = false
        // authStateFlow collector handles profile resolution cleanly without concurrent race
      },
      onAuthError = { msg ->
        _isSigningIn.value = false
        _authError.value = msg
      },
      onAuthCancelled = {
        _isSigningIn.value = false
      },
    )
  }

  fun retryProfileResolution() {
    val user = authRepository.currentUser()
    if (user == null) {
      _sessionState.value = AuthSessionState.SignedOut
    } else {
      resolveUserProfile(user, forceRefresh = true)
    }
  }

  fun signOut(credentialManager: CredentialManager) {
    viewModelScope.launch {
      _invitePreview.value = null
      _authError.value = null
      authRepository.signOut(credentialManager)
      _sessionState.value = AuthSessionState.SignedOut
    }
  }

  fun setDarkThemeOverride(enabled: Boolean?) {
    viewModelScope.launch { preferencesStore.setDarkThemeOverride(enabled) }
  }

  fun clearError() {
    _authError.value = null
  }

  fun deleteAccount(
    credentialManager: CredentialManager,
    workspaceId: String,
    isAdmin: Boolean,
    onCleanupBeforeDelete: suspend () -> Unit = {},
    onResult: (Result<Unit>) -> Unit = {},
  ) {
    if (_isDeletingAccount.value) return
    viewModelScope.launch {
      _isDeletingAccount.value = true
      runCatching { onCleanupBeforeDelete() }
      val result = authRepository.deleteCurrentAccount(credentialManager, workspaceId, isAdmin)
      _isDeletingAccount.value = false
      if (result.isSuccess) {
        _invitePreview.value = null
        _authError.value = null
        _sessionState.value = AuthSessionState.SignedOut
      }
      onResult(result)
    }
  }

  private fun resolveUserProfile(user: FirebaseUser, forceRefresh: Boolean) {
    viewModelScope.launch {
      resolveMutex.withLock {
        val currentState = _sessionState.value
        val pendingCode =
          _inviteCodeInput.value.ifBlank { preferencesStore.pendingInviteCode.firstOrNull().orEmpty() }
        if (!forceRefresh &&
          currentState is AuthSessionState.Authenticated &&
          currentState.profile.userId == user.uid &&
          pendingCode.isBlank()
        ) {
          return@withLock
        }
        _sessionState.value = AuthSessionState.ResolvingProfile
        val result = authRepository.ensureUserProfile(pendingCode.ifBlank { null })
        if (result.isSuccess) {
          preferencesStore.setPendingInviteCode(null)
          _inviteCodeInput.value = ""
          _sessionState.value = AuthSessionState.Authenticated(result.getOrThrow())
        } else {
          val msg = result.exceptionOrNull()?.message ?: "Failed to load user workspace profile."
          _authError.value = msg
          _sessionState.value = AuthSessionState.Blocked(msg)
        }
      }
    }
  }
}
