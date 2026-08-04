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

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secret_upao_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("upao_prefs_fallback", Context.MODE_PRIVATE)
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

    fun getSavedUser(): String? = prefs.getString("user_id", null)
    fun getSavedPass(): String? = prefs.getString("user_pass", null)

    fun setKeepLoggedIn(keep: Boolean) {
        prefs.edit().putBoolean("keep_logged_in", keep).apply()
    }

    fun shouldKeepLoggedIn(): Boolean = prefs.getBoolean("keep_logged_in", false)

    // ---------- Cuentas guardadas (multi-cuenta) ----------

    fun saveCuenta(usuario: String, password: String, nombre: String? = null) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        actuales.add(CuentaGuardada(usuario, password, nombre))
        prefs.edit().putString("cuentas_guardadas", guardarJson(actuales)).apply()
    }

    fun getCuentas(): List<CuentaGuardada> {
        val raw = prefs.getString("cuentas_guardadas", null) ?: return emptyList()
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
        prefs.edit().putString("cuentas_guardadas", guardarJson(actuales)).apply()
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
