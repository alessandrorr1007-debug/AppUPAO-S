package com.example.upaos

import com.example.upaos.data.refresh.*
import com.example.upaos.data.upao.*
import com.example.upaos.data.model.*
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RefreshIntegrityTest {
    @Test fun malformedBannerResponsesCannotBeReportedAsEmptySuccess() {
        for (json in listOf("invalid", "{}", "{\"data\":null}", "{\"data\":[1]}")) {
            try { parseBannerObjects(json); fail("Invalid schema must fail") } catch (_: UpaoUnexpectedFormatException) { }
        }
        assertTrue(parseBannerObjects("{\"data\":[],\"totalCount\":0}").isEmpty())
    }
    @Test fun missingSubcomponentReadProducesPartialAndPreservesLastValidCache() = runTest {
        val result = loadBannerGradeDetail({ listOf(JsonObject()) },
            { ComponenteDetalle(componentId = 1, hasSubComponents = true) }, { throw IOException("fixture") })
        assertTrue(result is RefreshResult.Partial)
        var cache = "last valid"
        try { publishComplete(result) { cache = "invalid" }; fail("Partial read must not publish") }
        catch (_: IncompleteRefreshException) { }
        assertEquals("last valid", cache)
    }
    @Test fun rootFailureAndTruncatedPageNeverReplaceGoodData() = runTest {
        val result = loadBannerGradeDetail({ throw IOException("fixture") }, { ComponenteDetalle() }, { emptyList() })
        assertTrue(result is RefreshResult.Error)
        try { parseBannerObjects("{\"data\":[],\"totalCount\":1}"); fail("Truncated page") }
        catch (_: IncompleteRefreshException) { }
        var published = false
        try { publishComplete(result) { published = true } } catch (_: IOException) { }
        assertFalse(published)
    }
    @Test fun failedWorkerCheckDoesNotResetFailuresEvenIfAnotherCheckSucceeds() = runTest {
        var calls = 0
        val result = runSyncChecks(listOf(
            { captureRefresh("attendance") { calls++; throw IOException("fixture") } },
            { captureRefresh("grades") { calls++; Unit } }
        ))
        var failures = 3
        try { publishComplete(result) { failures = 0 }; fail("Incomplete cycle") }
        catch (_: IncompleteRefreshException) { failures++ }
        assertEquals(2, calls)
        assertEquals(4, failures)
    }
    @Test fun completeEmptyResponseIsValidAndCancellationAlwaysPropagates() = runTest {
        val complete = loadBannerGradeDetail({ emptyList() }, { ComponenteDetalle() }, { emptyList() })
        assertTrue(complete.requireComplete().success)
        try { captureRefresh("fixture") { throw CancellationException("cancel") }; fail("Cancellation") }
        catch (_: CancellationException) { }
        try { loadBannerGradeDetail({ listOf(JsonObject()) }, { ComponenteDetalle(componentId=1,hasSubComponents=true) }, { throw CancellationException("cancel") }); fail("Cancellation") }
        catch (_: CancellationException) { }
    }
}
