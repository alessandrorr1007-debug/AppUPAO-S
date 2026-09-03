package com.example.upaos.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "theme_preferences")

class ThemePreferences(private val context: Context) {

    private val darkThemeKey = booleanPreferencesKey("dark_theme")

    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[darkThemeKey] ?: true }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[darkThemeKey] = enabled }
    }
}
