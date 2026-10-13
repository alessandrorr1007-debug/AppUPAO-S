package com.example.upaos

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SessionPreferencesTest {
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
