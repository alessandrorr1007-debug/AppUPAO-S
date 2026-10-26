package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CuentaGuardada(
    val usuario: String,
    val password: String,
    val nombre: String? = null,
    val nombreCorto: String? = null
)

class TokenManager(
    context: Context,
    customStore: SecureStore? = null,
    customLegacyPrefs: SharedPreferences? = null,
    customEncryptedLegacyPrefs: SharedPreferences? = null
) {

    private val store: SecureStore = customStore ?: SecureStore(context)

    private val fallbackPrefs: SharedPreferences = customLegacyPrefs
        ?: context.getSharedPreferences("upao_session_permanent", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        synchronized(migrationLock) {
            try {
                val encryptedLegacy = customEncryptedLegacyPrefs ?: if (customLegacyPrefs == null &&
                    context.getSharedPreferences("secret_upao_prefs", Context.MODE_PRIVATE).all.isNotEmpty()) {
                    abrirAlmacenAnterior(context)
                } else null
                encryptedLegacy?.let { migrarDesdeLegacySiAplica(it) }
                migrarDesdeLegacySiAplica(fallbackPrefs)
                val restoredUser = store.get("user_id")
                if (!restoredUser.isNullOrBlank() && !store.get("auth_token").isNullOrBlank()) {
                    com.example.upaos.data.upao.UpaoHttpClient.restoreAccount(restoredUser)
                }
            } catch (_: Exception) {
                Log.w(TAG, "No se pudo migrar la sesión; se conserva el almacén anterior")
            }
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
    @Suppress("DEPRECATION")
    private fun abrirAlmacenAnterior(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, "secret_upao_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrarDesdeLegacySiAplica(source: SharedPreferences) {
        try {
            val legacy = source.all
            if (legacy.isNotEmpty()) {
                val pending = mutableMapOf<String, String>()
                val currentUser = store.get("user_id")
                val legacyUser = legacy["user_id"] as? String
                val sessionKeys = setOf("user_id", "user_pass", "auth_token", "keep_logged_in")
                legacy.forEach { (key, value) ->
                    // Never attach an old account's password/token to a newer active account.
                    if (key in sessionKeys && currentUser != null && currentUser != legacyUser) return@forEach
                    val oldValue = when (value) {
                        is String -> value
                        is Boolean, is Number -> value.toString()
                        else -> return@forEach
                    }
                    val current = store.get(key)
                    check(!store.contains(key) || current != null) { "Almacén nuevo no disponible" }
                    if (key == "cuentas_guardadas") {
                        val type = object : TypeToken<List<CuentaGuardada>>() {}.type
                        val oldAccounts = gson.fromJson<List<CuentaGuardada>>(oldValue, type).orEmpty()
                        val newAccounts = current?.let { gson.fromJson<List<CuentaGuardada>>(it, type) }.orEmpty()
                        pending[key] = gson.toJson((oldAccounts + newAccounts).associateBy { it.usuario }.values.toList())
                    } else if (current == null) {
                        pending[key] = oldValue
                    }
                }
                store.putAll(pending)
                check(pending.all { (key, value) -> store.get(key) == value })
                check(source.edit().clear().commit())
                Log.i(TAG, "Migración a SecureStore completada")
            }
        } catch (_: Exception) {
            Log.w(TAG, "Error durante la migración; se preserva almacén antiguo")
        }
    }

    fun saveToken(token: String) {
        store.put("auth_token", token)
    }

    fun getToken(): String? {
        return store.get("auth_token")
    }

    fun saveCredentials(user: String, pass: String) {
        store.putAll(mapOf("user_id" to user, "user_pass" to pass))
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

    /** Persist the complete login before allowing navigation to the authenticated UI. */
    fun saveLogin(user: String, password: String?, keep: Boolean, name: String? = null) = synchronized(SessionCleanup.lock) {
        val savedAccounts = getCuentas()
        val previous = savedAccounts.firstOrNull { it.usuario == user }
        val accounts = savedAccounts.filterNot { it.usuario == user }.toMutableList()
        val displayName = if (!previous?.nombreCorto.isNullOrBlank()) previous?.nombre else name ?: previous?.nombre
        val shortName = previous?.nombreCorto ?: com.example.upaos.data.profile.StudentProfile.shortNameFromFullName(displayName)
        if (password != null) accounts.add(CuentaGuardada(user, password, displayName, shortName))
        val values = mutableMapOf(
            "user_id" to user, "auth_token" to user, "keep_logged_in" to keep.toString(),
            "cuentas_guardadas" to gson.toJson(accounts),
            "session_id" to java.util.UUID.randomUUID().toString()
        )
        if (password != null) values["user_pass"] = password
        store.putAll(values, if (password == null) setOf("user_pass") else emptySet())
    }

    /** Persistent generation lets receivers reject alarms from a previous login after process death. */
    fun getSessionId(): String? {
        if (getSavedUser().isNullOrBlank() || getToken().isNullOrBlank()) return null
        synchronized(migrationLock) {
            return store.get("session_id") ?: java.util.UUID.randomUUID().toString().also { store.put("session_id", it) }
        }
    }

    fun saveCuenta(usuario: String, password: String, nombre: String? = null) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        val shortName = com.example.upaos.data.profile.StudentProfile.shortNameFromFullName(nombre)
        actuales.add(CuentaGuardada(usuario, password, nombre, shortName))
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

    fun getProfileJson(usuario: String): String? = store.get(profileKey(usuario))

    fun saveProfileIfCurrent(usuario: String, sessionId: String, json: String,
        fullName: String? = null, shortName: String? = null): Boolean = synchronized(SessionCleanup.lock) {
        if (getSavedUser() != usuario || getSessionId() != sessionId || AccountPrivacy.isDeleting(usuario)) return@synchronized false
        val values = mutableMapOf(profileKey(usuario) to json)
        if (!fullName.isNullOrBlank() || !shortName.isNullOrBlank()) {
            var changed = false
            val accounts = getCuentas().map { account ->
                if (account.usuario != usuario) account else account.copy(
                    nombre = fullName?.takeIf { it.isNotBlank() } ?: account.nombre,
                    nombreCorto = shortName?.takeIf { it.isNotBlank() } ?: account.nombreCorto
                ).also { if (it != account) changed = true }
            }
            if (changed) values["cuentas_guardadas"] = gson.toJson(accounts)
        }
        store.putAll(values)
        true
    }

    private fun profileKey(usuario: String) = "student_profile_${usuario.trim().lowercase(java.util.Locale.ROOT)}"

    fun removeCuenta(usuario: String) {
        val actuales = getCuentas().toMutableList()
        actuales.removeAll { it.usuario == usuario }
        val json = guardarJson(actuales)
        store.putAll(mapOf("cuentas_guardadas" to json), setOf(profileKey(usuario)))
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
    fun clearSession() = synchronized(SessionCleanup.lock) {
        val removals = mutableSetOf("auth_token", "user_id", "user_pass", "session_id")
        getSavedUser()?.let { removals.add(profileKey(it)) }
        store.putAll(mapOf("keep_logged_in" to "false"), removals)
    }

    fun clearAll() {
        store.clearAll()
    }
}
