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

    suspend fun obtenerCursosActuales(usuario: String?): List<String> {
        val prefs = context.gradesDataStore.data.first()
        val gson = com.google.gson.Gson()
        val nombres = linkedSetOf<String>()

        val userKey = usuario?.trim()?.lowercase() ?: "anonimo"
        val posiblesClaves = listOf(
            "notas_$userKey",
            userKey,
            "202610_UG",
            "202610_EPG",
            "202520_UG",
            "202510_UG"
        )

        for (clave in posiblesClaves) {
            val json = prefs[stringPreferencesKey("notas_$clave")]
            if (!json.isNullOrBlank()) {
                try {
                    val resp = gson.fromJson(json, com.example.upaos.data.model.GradesResponse::class.java)
                    val lista = resp.cursos.mapNotNull { it.displayNombre.trim() }.filter { it.isNotBlank() }
                    if (lista.isNotEmpty()) {
                        nombres.addAll(lista)
                        return nombres.toList()
                    }
                } catch (_: Exception) {}
            }
        }

        // Si no se encontró por clave directa, buscar en todas las preferencias de notas
        for ((key, value) in prefs.asMap()) {
            if (value is String && value.contains("\"cursos\"")) {
                try {
                    val resp = gson.fromJson(value, com.example.upaos.data.model.GradesResponse::class.java)
                    val lista = resp.cursos.mapNotNull { it.displayNombre.trim() }.filter { it.isNotBlank() }
                    if (lista.isNotEmpty()) {
                        nombres.addAll(lista)
                    }
                } catch (_: Exception) {}
            }
        }

        return nombres.toList()
    }
}
