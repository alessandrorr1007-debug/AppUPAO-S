package com.example.upaos

import com.example.upaos.data.local.SessionCleanup
import com.example.upaos.service.ReminderOwner
import org.junit.Assert.*
import org.junit.Test

class SessionCleanupTest {
    @Test fun logoutInvalidatesBeforeCancellingAllAccountTasksAndKeepsRememberedAccounts() {
        val remembered = listOf("000111111", "000222222")
        var currentUser: String? = remembered.first()
        var currentSession: String? = "old-session"
        val alarm = ReminderOwner(currentUser!!, currentSession!!)
        val calls = mutableListOf<String>()
        SessionCleanup.logout(
            { currentUser = null; currentSession = null; calls += "invalidate" },
            { assertFalse(alarm.matches(currentUser, currentSession)); calls += "cookies" },
            { calls += "alarms" }, { calls += "sync" }, { calls += "agenda" }
        )
        assertEquals(listOf("invalidate", "cookies", "alarms", "sync", "agenda"), calls)
        assertEquals(2, remembered.size)
        assertFalse(alarm.matches(currentUser, currentSession))
    }
    @Test fun lateWorkerCannotPublishAfterLogoutOrAfterAnotherLoginOfSameUser() {
        val old = ReminderOwner("000111111", "old")
        var current: Pair<String?, String?> = "000111111" to "old"
        val notifications = mutableListOf<String>()
        assertTrue(SessionCleanup.publishIfCurrent(old, { current }) { notifications += "before" })
        SessionCleanup.logout({ current = null to null }, {}, { notifications.clear() }, {}, {})
        assertFalse(SessionCleanup.publishIfCurrent(old, { current }) { notifications += "late" })
        current = "000111111" to "new"
        assertFalse(SessionCleanup.publishIfCurrent(old, { current }) { notifications += "old login" })
        assertTrue(notifications.isEmpty())
    }
    @Test fun oldRepositoryCannotResurrectALoggedOutAccount() {
        val a = com.example.upaos.data.upao.UpaoHttpClient.selectAccount("000111111")
        com.example.upaos.data.upao.UpaoHttpClient.clearAccount(a.user)
        try { com.example.upaos.data.upao.UpaoHttpClient.forAccount(a.user); fail("Logged out session must remain invalid") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }
}
