package com.example.upaos

import com.example.upaos.data.local.UnlockEvent
import com.example.upaos.data.local.remainsLocked
import org.junit.Assert.*
import org.junit.Test

class AppUnlockTest {
    @Test fun unavailableOrFailedAuthenticationNeverUnlocks() {
        assertTrue(remainsLocked(UnlockEvent.Unavailable))
        assertTrue(remainsLocked(UnlockEvent.Failed))
    }
    @Test fun onlyVerifiedCurrentAccountOrDeviceCredentialUnlocks() {
        assertFalse(remainsLocked(UnlockEvent.DeviceAuthenticated))
        assertFalse(remainsLocked(UnlockEvent.UpaoAuthenticated("000111111", "000111111")))
        assertTrue(remainsLocked(UnlockEvent.UpaoAuthenticated("000111111", "000222222")))
        assertTrue(remainsLocked(UnlockEvent.UpaoAuthenticated(null, "000111111")))
    }
}
