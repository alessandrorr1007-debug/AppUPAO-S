package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class CuentaGuardada(
    val usuario: String,
    val password: String,
    val nombre: String? = null
)

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val fallbackPrefs: SharedPreferences =
        context.getSharedPreferences("upao_session_permanent", Context.MODE_PRIVATE)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_upao_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        synchronized(migrationLock) {
            val legacy = fallbackPrefs.all
            if (legacy.isNotEmpty()) {
                val editor = prefs.edit()
                legacy.forEach { (key, value) ->
                    if (!prefs.contains(key)) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                        }
                    }
                }
                check(editor.commit()) { "No se pudieron proteger las credenciales guardadas" }
                check(fallbackPrefs.edit().clear().commit()) { "No se pudo limpiar el respaldo antiguo" }
            }
        }
    }

    companion object {
        private val migrationLock = Any()
    }

    fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun saveCredentials(user: String, pass: String) {
        prefs.edit().putString("user_id", user).putString("user_pass", pass).apply()
    }

    fun saveUserId(user: String) {
        prefs.edit().putString("user_id", user).apply()
    }

    fun getSavedUser(): String? =
        prefs.getString("user_id", null)

    fun containsAccount(user: String): Boolean = getSavedUser() == user || getCuentas().any { it.usuario == user }

    fun forgetCurrentPassword() {
        prefs.edit().remove("user_pass").apply()
        getSavedUser()?.let { removeCuenta(it) }
    }

    fun getSavedPass(): String? {
        val pass = prefs.getString("user_pass", null)
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
        prefs.edit().putBoolean("keep_logged_in", keep).apply()
    }

    fun shouldKeepLoggedIn(): Boolean {
        val user = getSavedUser()
        if (user.isNullOrBlank()) return false
        return prefs.getBoolean("keep_logged_in", true)
    }

    // ---------- Cuentas guardadas (multi-cuenta) ----------

    fun saveCuenta(usuario: String, password: String, nombre: String? = null) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        actuales.add(CuentaGuardada(usuario, password, nombre))
        val json = guardarJson(actuales)
        prefs.edit().putString("cuentas_guardadas", json).apply()
    }

    fun getCuentas(): List<CuentaGuardada> {
        val raw = prefs.getString("cuentas_guardadas", null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                CuentaGuardada(
                    usuario = o.getString("usuario"),
                    password = o.getString("password"),
                    nombre = o.optString("nombre").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeCuenta(usuario: String) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        val json = guardarJson(actuales)
        prefs.edit().putString("cuentas_guardadas", json).apply()
    }

    private fun guardarJson(cuentas: List<CuentaGuardada>): String {
        val arr = JSONArray()
        cuentas.forEach { c ->
            val o = JSONObject()
            o.put("usuario", c.usuario)
            o.put("password", c.password)
            if (c.nombre != null) o.put("nombre", c.nombre)
            arr.put(o)
        }
        return arr.toString()
    }

    // ---------- Canvas Token ----------

    fun saveCanvasToken(usuario: String, token: String) {
        val key = "canvas_token_${usuario.trim().lowercase()}"
        prefs.edit().putString(key, token).apply()
    }

    fun getCanvasToken(usuario: String): String? {
        val key = "canvas_token_${usuario.trim().lowercase()}"
        return prefs.getString(key, null)
    }

    fun removeCanvasToken(usuario: String) {
        val key = "canvas_token_${usuario.trim().lowercase()}"
        prefs.edit().remove(key).apply()
    }

    /** Cierra la sesión actual sin borrar las cuentas guardadas del dispositivo. */
    fun clearSession() {
        prefs.edit()
            .remove("auth_token")
            .remove("user_id")
            .remove("user_pass")
            .putBoolean("keep_logged_in", false)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
