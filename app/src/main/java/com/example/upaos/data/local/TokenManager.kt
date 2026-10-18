package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CuentaGuardada(
    val usuario: String,
    val password: String,
    val nombre: String? = null
)

class TokenManager(
    context: Context,
    customStore: SecureStore? = null,
    customLegacyPrefs: SharedPreferences? = null
) {

    private val store: SecureStore = customStore ?: SecureStore(context)

    private val fallbackPrefs: SharedPreferences = customLegacyPrefs
        ?: context.getSharedPreferences("upao_session_permanent", Context.MODE_PRIVATE)

    init {
        synchronized(migrationLock) {
            migrarDesdeLegacySiAplica()
        }
    }

    companion object {
        private const val TAG = "TokenManager"
        private val migrationLock = Any()
    }

    /**
     * Migra datos desde el almacenamiento antiguo sin pérdida de datos.
     * Verifica que los datos migrados se lean correctamente antes de limpiar el almacén anterior.
     * Respeta todas las cuentas guardadas (multi-cuenta).
     */
    private fun migrarDesdeLegacySiAplica() {
        try {
            val legacy = fallbackPrefs.all
            if (legacy.isNotEmpty()) {
                var migradosExitosamente = true

                // 1. Escribir datos en SecureStore
                legacy.forEach { (key, value) ->
                    if (!store.contains(key)) {
                        when (value) {
                            is String -> store.put(key, value)
                            is Boolean -> store.putBoolean(key, value)
                            is Number -> store.put(key, value.toString())
                        }
                    }
                }

                // 2. Verificar que todos los datos clave se leen correctamente en SecureStore
                legacy.forEach { (key, value) ->
                    val readBack = store.get(key)
                    if (readBack == null && value != null) {
                        migradosExitosamente = false
                    }
                }

                // 3. Solo si todo se verificó con éxito, limpiar el almacén viejo
                if (migradosExitosamente) {
                    val cleared = fallbackPrefs.edit().clear().commit()
                    if (!cleared) {
                        Log.w(TAG, "No se pudo limpiar el respaldo antiguo tras migración exitosa")
                    } else {
                        Log.i(TAG, "Migración a SecureStore completada con éxito")
                    }
                } else {
                    Log.w(TAG, "La verificación de migración falló. Se preserva el almacén antiguo.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la migración a SecureStore; se preserva almacén antiguo", e)
        }
    }

    fun saveToken(token: String) {
        store.put("auth_token", token)
    }

    fun getToken(): String? {
        return store.get("auth_token")
    }

    fun saveCredentials(user: String, pass: String) {
        store.put("user_id", user)
        store.put("user_pass", pass)
    }

    fun saveUserId(user: String) {
        store.put("user_id", user)
    }

    fun getSavedUser(): String? =
        store.get("user_id")

    fun containsAccount(user: String): Boolean =
        getSavedUser() == user || getCuentas().any { it.usuario == user }

    fun forgetCurrentPassword() {
        store.remove("user_pass")
        getSavedUser()?.let { removeCuenta(it) }
    }

    fun getSavedPass(): String? {
        val pass = store.get("user_pass")
        if (!pass.isNullOrBlank()) return pass
        // Respaldo: buscar en las cuentas guardadas
        val user = getSavedUser()
        if (!user.isNullOrBlank()) {
            val cuenta = getCuentas().firstOrNull { it.usuario.equals(user, ignoreCase = true) }
            if (cuenta != null && cuenta.password.isNotBlank()) {
                saveCredentials(user, cuenta.password)
                return cuenta.password
            }
        }
        return null
    }

    fun setKeepLoggedIn(keep: Boolean) {
        store.putBoolean("keep_logged_in", keep)
    }

    fun shouldKeepLoggedIn(): Boolean {
        val user = getSavedUser()
        if (user.isNullOrBlank()) return false
        return store.getBoolean("keep_logged_in", true)
    }

    private val gson = Gson()

    fun saveCuenta(usuario: String, password: String, nombre: String? = null) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        actuales.add(CuentaGuardada(usuario, password, nombre))
        val json = guardarJson(actuales)
        store.put("cuentas_guardadas", json)
    }

    fun getCuentas(): List<CuentaGuardada> {
        val raw = store.get("cuentas_guardadas") ?: return emptyList()
        return try {
            val listType = object : TypeToken<List<CuentaGuardada>>() {}.type
            gson.fromJson<List<CuentaGuardada>>(raw, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeCuenta(usuario: String) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        val json = guardarJson(actuales)
        store.put("cuentas_guardadas", json)
    }

    private fun guardarJson(cuentas: List<CuentaGuardada>): String {
        return gson.toJson(cuentas)
    }

    // ---------- Canvas Token ----------

    fun saveCanvasToken(usuario: String, token: String) {
        val key = "canvas_token_${usuario.trim().lowercase()}"
        store.put(key, token)
    }

    fun getCanvasToken(usuario: String): String? {
        val key = "canvas_token_${usuario.trim().lowercase()}"
        return store.get(key)
    }

    fun removeCanvasToken(usuario: String) {
        val key = "canvas_token_${usuario.trim().lowercase()}"
        store.remove(key)
    }

    /** Cierra la sesión actual sin borrar las cuentas guardadas del dispositivo. */
    fun clearSession() {
        store.remove("auth_token")
        store.remove("user_id")
        store.remove("user_pass")
        store.putBoolean("keep_logged_in", false)
    }

    fun clearAll() {
        store.clearAll()
    }
}
