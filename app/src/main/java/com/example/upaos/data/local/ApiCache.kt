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

    /**
     * Devuelve los valores en caché (JSON) de las claves que empiecen
     * por [prefijo]. Útil para el widget de próxima clase, que no conoce
     * el periodo seleccionado y debe localizar cualquier horario guardado.
     */
    suspend fun listarPorPrefijo(prefijo: String): Map<String, String> {
        val prefs = context.apiDataStore.data.first()
        return prefs.asMap()
            .filterKeys { it.name.startsWith(prefijo) }
            .mapKeys { it.key.name }
            .mapValues { it.value as? String ?: "" }
            .filterValues { it.isNotBlank() }
    }
}
