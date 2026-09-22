package com.example.upaos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.upaos.data.model.CanvasTodoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.canvasDataStore by preferencesDataStore("canvas_cache")

class CanvasCache(private val context: Context) {
    private val gson = Gson()

    private fun jsonKey(user: String) = stringPreferencesKey("canvas_todo_${user.trim().lowercase()}")
    private fun timeKey(user: String) = longPreferencesKey("canvas_time_${user.trim().lowercase()}")

    suspend fun guardar(user: String, items: List<CanvasTodoItem>) {
        val json = gson.toJson(items)
        context.canvasDataStore.edit { prefs ->
            prefs[jsonKey(user)] = json
            prefs[timeKey(user)] = System.currentTimeMillis()
        }
    }

    suspend fun cargar(user: String): Pair<List<CanvasTodoItem>, Long?> {
        val prefs = context.canvasDataStore.data.first()
        val json = prefs[jsonKey(user)]
        val timestamp = prefs[timeKey(user)]

        if (json.isNullOrBlank()) {
            return Pair(emptyList(), null)
        }

        return try {
            val type = object : TypeToken<List<CanvasTodoItem>>() {}.type
            val list = gson.fromJson<List<CanvasTodoItem>>(json, type) ?: emptyList()
            Pair(list, timestamp)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }

    suspend fun limpiar(user: String) {
        context.canvasDataStore.edit { prefs ->
            prefs.remove(jsonKey(user))
            prefs.remove(timeKey(user))
        }
    }
}
