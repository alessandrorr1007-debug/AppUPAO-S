package com.example.upaos

import com.example.upaos.service.ReminderOwner
import com.example.upaos.service.reminderScheduleKey
import org.junit.Assert.*
import org.junit.Test

class ReminderOwnershipTest {
    @Test fun aMissingAccountScheduleNeverSelectsAnotherAccountsCache() {
        val cached = mapOf("horario_000111111_202620" to "account-a")
        val bKey = reminderScheduleKey("000222222", "202620")
        assertNull(cached[bKey])
        assertEquals("account-a", cached[reminderScheduleKey("000111111", "202620")])
        assertNull(reminderScheduleKey("000222222", null))
        assertNull(reminderScheduleKey("000222222", "202690"))
    }
    @Test fun ownerAndSessionGenerationMustMatch() {
        val a = ReminderOwner("000111111", "login-a")
        assertTrue(a.matches("000111111", "login-a"))
        assertFalse(a.matches("000222222", "login-a"))
        assertFalse(a.matches("000111111", "new-login"))
        assertFalse(a.matches(null, null))
        assertNotEquals(a.identity(1), ReminderOwner("000222222", "login-b").identity(1))
    }
}
