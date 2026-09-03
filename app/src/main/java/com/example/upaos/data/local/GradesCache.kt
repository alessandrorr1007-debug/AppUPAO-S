package com.example.upaos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.gradesDataStore by preferencesDataStore("notas_cache")

/**
 * Caché local de la última respuesta exitosa de /notas/buscar.
 * Se guarda el JSON COMPLETO de la respuesta (incluye el campo
 * ultima_actualizacion real del backend), así el timestamp siempre viaja
 * junto con los datos que le corresponden.
 */
class GradesCache(private val context: Context) {

    suspend fun guardar(clave: String, json: String) {
        context.gradesDataStore.edit { prefs ->
            prefs[stringPreferencesKey("notas_$clave")] = json
        }
    }

    suspend fun cargar(clave: String): String? {
        val prefs = context.gradesDataStore.data.first()
        return prefs[stringPreferencesKey("notas_$clave")]
    }
}
