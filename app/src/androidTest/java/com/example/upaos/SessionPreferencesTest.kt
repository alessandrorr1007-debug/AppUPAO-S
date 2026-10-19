package com.example.upaos

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.local.SecureStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SessionPreferencesTest {
    @Test fun keystoreSessionSurvivesStoreRecreation() {
        val first = TokenManager(context)
        first.saveLogin("000000001", "synthetic-password", true)
        val second = TokenManager(context)
        assertEquals("000000001", second.getSavedUser())
        assertEquals("synthetic-password", second.getSavedPass())
        assertTrue(second.shouldKeepLoggedIn())
        val store = SecureStore(context)
        store.put("probe", "synthetic")
        val prefs = context.getSharedPreferences("upao_secure_store", Context.MODE_PRIVATE)
        val ciphertext = prefs.getString("probe", null)
        store.put("probe", "synthetic")
        assertNotEquals(ciphertext, prefs.getString("probe", null))
        assertEquals("synthetic", SecureStore(context).get("probe"))
    }

    @Suppress("DEPRECATION")
    @Test fun encryptedPreferencesFromPreviousVersionAreMigrated() {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val old = EncryptedSharedPreferences.create(context, "secret_upao_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        assertTrue(old.edit().putString("user_id", "000000001")
            .putString("user_pass", "synthetic-password").putBoolean("keep_logged_in", true).commit())
        val migrated = TokenManager(context)
        assertEquals("000000001", migrated.getSavedUser())
        assertEquals("synthetic-password", migrated.getSavedPass())
        assertTrue(migrated.shouldKeepLoggedIn())
        assertTrue(old.all.isEmpty())
    }
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val names = mutableSetOf<String>()
        private val prefix = "regression_${System.nanoTime()}_"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = prefix + name
            names.add(isolated)
            return super.getSharedPreferences(isolated, mode)
        }
    }

    @After fun cleanTestPreferences() {
        context.names.forEach { context.baseContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun legacyPasswordIsMigratedAndPlaintextBackupIsCleared() {
        val legacy = context.getSharedPreferences("upao_session_permanent", Context.MODE_PRIVATE)
        legacy.edit().putString("user_id", "000000001").putString("user_pass", "test-password").putBoolean("keep_logged_in", false).commit()
        val manager = TokenManager(context)
        assertEquals("test-password", manager.getSavedPass())
        assertTrue(legacy.all.isEmpty())
        assertFalse(manager.shouldKeepLoggedIn())
    }

    @Test fun savingOrRefreshingCredentialsDoesNotEnableRememberSession() {
        val manager = TokenManager(context)
        manager.saveCredentials("000000001", "test-password")
        manager.setKeepLoggedIn(false)
        manager.saveCredentials("000000001", "new-test-password")
        manager.saveUserId("000000001")
        assertFalse(manager.shouldKeepLoggedIn())
        manager.saveCuenta("000000001", "new-test-password")
        manager.forgetCurrentPassword()
        assertNull(manager.getSavedPass())
        assertTrue(manager.getCuentas().isEmpty())
    }

    @Test fun notificationSnapshotsBelongToTheirAccount() {
        val manager = TokenManager(context)
        manager.saveUserId("000000001")
        NotificationPreferences(context).ultimoSnapshotNotas = "first-account-grades"
        manager.saveUserId("000000002")
        assertNull(NotificationPreferences(context).ultimoSnapshotNotas)
        NotificationPreferences(context).ultimoSnapshotNotas = "second-account-grades"
        manager.saveUserId("000000001")
        assertEquals("first-account-grades", NotificationPreferences(context).ultimoSnapshotNotas)
    }
}

/** Can run write/read in separate instrumentation processes via -e sessionPhase. */
class KeystoreProcessRestartTest {
    @Test fun sessionSurvivesProcessRestart() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("session_restart_probe_$name", mode)
        }
        val phase = InstrumentationRegistry.getArguments().getString("sessionPhase")
        if (phase != "read") {
            TokenManager(context).saveLogin("000000001", "synthetic-password", true)
        }
        if (phase != "write") {
            val recreated = TokenManager(context)
            assertEquals("000000001", recreated.getSavedUser())
            assertEquals("synthetic-password", recreated.getSavedPass())
            assertTrue(recreated.shouldKeepLoggedIn())
            assertEquals(1, recreated.getCuentas().size)
            recreated.clearAll()
        }
    }
}
