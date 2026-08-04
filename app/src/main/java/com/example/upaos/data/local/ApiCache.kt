package com.example.upaos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.apiDataStore by preferencesDataStore("api_cache")

/**
 * Caché local de la última respuesta exitosa de los endpoints de la app
 * (asistencia, horario, etc.). Se guarda el JSON COMPLETO de la respuesta,
 * igual que GradesCache, para mostrar al instante lo último conocido
 * sin que parezca que está cargando.
 */
class ApiCache(private val context: Context) {

    suspend fun guardar(clave: String, json: String) {
        context.apiDataStore.edit { prefs ->
            prefs[stringPreferencesKey(clave)] = json
        }
    }

    suspend fun cargar(clave: String): String? {
        val prefs = context.apiDataStore.data.first()
        return prefs[stringPreferencesKey(clave)]
    }
}
