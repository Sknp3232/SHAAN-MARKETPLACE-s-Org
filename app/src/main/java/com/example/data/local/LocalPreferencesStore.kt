package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "smart_sheet_prefs")

class LocalPreferencesStore(private val context: Context) {
  private val pendingInviteCodeKey = stringPreferencesKey("pending_invite_code")
  private val darkThemeOverrideKey = booleanPreferencesKey("dark_theme_override")
  private val hasThemeOverrideKey = booleanPreferencesKey("has_theme_override")

  val pendingInviteCode: Flow<String?> =
    context.dataStore.data.map { prefs -> prefs[pendingInviteCodeKey]?.takeIf { it.isNotBlank() } }

  val darkThemePreference: Flow<Boolean?> =
    context.dataStore.data.map { prefs ->
      if (prefs[hasThemeOverrideKey] == true) prefs[darkThemeOverrideKey] else null
    }

  suspend fun setPendingInviteCode(code: String?) {
    context.dataStore.edit { prefs ->
      if (code.isNullOrBlank()) {
        prefs.remove(pendingInviteCodeKey)
      } else {
        prefs[pendingInviteCodeKey] = code.trim().uppercase()
      }
    }
  }

  suspend fun setDarkThemeOverride(enabled: Boolean?) {
    context.dataStore.edit { prefs ->
      if (enabled == null) {
        prefs.remove(hasThemeOverrideKey)
        prefs.remove(darkThemeOverrideKey)
      } else {
        prefs[hasThemeOverrideKey] = true
        prefs[darkThemeOverrideKey] = enabled
      }
    }
  }
}
