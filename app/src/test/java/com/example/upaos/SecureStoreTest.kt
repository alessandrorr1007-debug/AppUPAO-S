package com.example.upaos

import android.content.SharedPreferences
import com.example.upaos.data.local.CuentaGuardada
import com.example.upaos.data.local.SecureStore
import com.example.upaos.data.local.TokenManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecureStoreTest {

    private lateinit var testKey: SecretKey
    private lateinit var mockPrefs: FakeSharedPreferences
    private lateinit var secureStore: SecureStore

    @Before
    fun setUp() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        testKey = keyGen.generateKey()
        mockPrefs = FakeSharedPreferences()
        secureStore = SecureStore(
            context = FakeContext(),
            customKey = testKey,
            customPrefs = mockPrefs
        )
    }

    @Test
    fun `test cifrar y descifrar valor con AES-256-GCM`() {
        val clave = "password_alumno"
        val valorOriginal = "MiClaveSuperSegura123!"

        secureStore.put(clave, valorOriginal)

        // El valor guardado en SharedPreferences no debe estar en texto plano
        val rawGuardado = mockPrefs.getString(clave, null)
        assertNotNull(rawGuardado)
        assertTrue(rawGuardado != valorOriginal)

        // Al recuperarlo a través de SecureStore debe coincidir exactamente
        val recuperado = secureStore.get(clave)
        assertEquals(valorOriginal, recuperado)
    }

    @Test
    fun `test valor inexistente retorna null y boolean por defecto`() {
        assertNull(secureStore.get("clave_inexistente"))
        assertFalse(secureStore.contains("clave_inexistente"))
        assertTrue(secureStore.getBoolean("clave_inexistente", true))
        assertFalse(secureStore.getBoolean("clave_inexistente", false))
    }

    @Test
    fun `test remove y clearAll eliminan valores correctamente`() {
        secureStore.put("k1", "v1")
        secureStore.put("k2", "v2")
        assertTrue(secureStore.contains("k1"))
        assertTrue(secureStore.contains("k2"))

        secureStore.remove("k1")
        assertNull(secureStore.get("k1"))
        assertEquals("v2", secureStore.get("k2"))

        secureStore.clearAll()
        assertNull(secureStore.get("k2"))
        assertFalse(secureStore.contains("k2"))
    }

    @Test
    fun `test migracion desde formato viejo con 2 o mas cuentas sin perdida de datos`() {
        val legacyPrefs = FakeSharedPreferences()
        val jsonCuentas = """
            [
                {"usuario":"000123456","password":"password1","nombre":"Juan Perez"},
                {"usuario":"000654321","password":"password2","nombre":"Maria Gomez"}
            ]
        """.trimIndent()

        legacyPrefs.edit()
            .putString("user_id", "000123456")
            .putString("user_pass", "password1")
            .putString("auth_token", "jwt_token_sample_abc123")
            .putBoolean("keep_logged_in", true)
            .putString("cuentas_guardadas", jsonCuentas)
            .commit()

        val tokenManager = TokenManager(
            context = FakeContext(),
            customStore = secureStore,
            customLegacyPrefs = legacyPrefs
        )

        // 1. Verificar sesión principal
        assertEquals("000123456", tokenManager.getSavedUser())
        assertEquals("password1", tokenManager.getSavedPass())
        assertEquals("jwt_token_sample_abc123", tokenManager.getToken())
        assertTrue(tokenManager.shouldKeepLoggedIn())

        // 2. Verificar multi-cuenta (ambas cuentas sobrevivieron)
        val cuentas = tokenManager.getCuentas()
        assertEquals(2, cuentas.size)

        val cuenta1 = cuentas.find { it.usuario == "000123456" }
        assertNotNull(cuenta1)
        assertEquals("password1", cuenta1?.password)
        assertEquals("Juan Perez", cuenta1?.nombre)

        val cuenta2 = cuentas.find { it.usuario == "000654321" }
        assertNotNull(cuenta2)
        assertEquals("password2", cuenta2?.password)
        assertEquals("Maria Gomez", cuenta2?.nombre)

        // 3. Verificar que el almacén viejo se limpió tras verificar la migración exitosa
        assertTrue("El almacén legacy debe quedar vacío tras migración exitosa", legacyPrefs.all.isEmpty())
    }

    // --- Fake SharedPreferences y Context para tests JVM sin dependencias de Android framework ---

    private class FakeContext : android.content.ContextWrapper(null)

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Number)?.toInt() ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Number)?.toLong() ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Number)?.toFloat() ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class Editor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = HashMap(prefs.data)

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    if (value != null) temp[key] = value else temp.remove(key)
                }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) temp.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                prefs.data.clear()
                prefs.data.putAll(temp)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
