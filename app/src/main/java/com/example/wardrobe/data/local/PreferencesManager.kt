package com.example.wardrobe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wardrobe_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_STYLE_PREF = stringPreferencesKey("style_preference")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // SYSTEM, LIGHT, DARK
        val KEY_NOTIFS_ENABLED = booleanPreferencesKey("notifs_enabled")
        val KEY_FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_AUTH_INTRO_COMPLETED = booleanPreferencesKey("auth_intro_completed")
        val KEY_UNITS = stringPreferencesKey("units")
    }

    val userName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_NAME] ?: "Alex"
    }

    val stylePreference: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_STYLE_PREF] ?: "Modern Minimalist"
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "SYSTEM"
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFS_ENABLED] ?: false
    }

    val isFirstLaunchDone: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIRST_LAUNCH_DONE] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val isAuthIntroCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTH_INTRO_COMPLETED] ?: false
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_NAME] = name
        }
    }

    suspend fun setStylePreference(style: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STYLE_PREF] = style
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFS_ENABLED] = enabled
        }
    }

    suspend fun setFirstLaunchDone(done: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_LAUNCH_DONE] = done
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
            preferences[KEY_FIRST_LAUNCH_DONE] = true
        }
    }

    suspend fun setAuthIntroCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTH_INTRO_COMPLETED] = completed
        }
    }

    suspend fun resetAuthIntro() {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTH_INTRO_COMPLETED] = false
        }
    }
}
