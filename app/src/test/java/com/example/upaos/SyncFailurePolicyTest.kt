package com.example.upaos

import com.example.upaos.service.isFatalSyncFailure
import com.example.upaos.data.refresh.*
import com.example.upaos.data.upao.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SyncFailurePolicyTest {
    @Test fun partialNetworkErrorsKeepBackoffButFormatAndSessionFailuresStopPolling() {
        assertFalse(isFatalSyncFailure(IncompleteRefreshException(listOf(RefreshIssue("fixture", IOException())))))
        for (error in listOf(UpaoUnexpectedFormatException(), UpaoSessionExpiredException(), UpaoInvalidCredentialsException())) {
            assertTrue(isFatalSyncFailure(IncompleteRefreshException(listOf(RefreshIssue("fixture", error)))))
        }
    }
}
