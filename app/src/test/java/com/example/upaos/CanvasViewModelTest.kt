package com.example.upaos

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.upaos.data.api.*
import com.example.upaos.data.canvas.*
import com.example.upaos.data.local.CanvasCacheStore
import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.*
import com.example.upaos.ui.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CanvasViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun providerRetainsInflightResourceAndCoalescesRefreshes() = runTest {
        var calls=0
        val ready=CompletableDeferred<List<Int>>()
        val store=ViewModelStore()
        val factory=canvasViewModelFactory {
            CanvasResourceViewModel(CanvasResourceRepository { calls++; ready.await() },emptyList<Int>())
        }
        val first=ViewModelProvider(store,factory)["fixture",CanvasResourceViewModel::class.java]
        runCurrent()
        repeat(4) { first.refresh(true) }
        val recreated=ViewModelProvider(store,factory)["fixture",CanvasResourceViewModel::class.java]
        assertSame(first,recreated)
        assertEquals(1,calls)
        ready.complete(emptyList()); runCurrent()
        assertFalse(first.state.value.loading)
        assertEquals(emptyList<Int>(),first.state.value.data)
        store.clear()
    }

    @Test fun refreshFailurePreservesPreviouslyLoadedDataAndExposesError() = runTest {
        var fail=false
        val vm=CanvasResourceViewModel(CanvasResourceRepository {
            if (fail) throw IOException("fixture offline")
            listOf(7)
        }, emptyList<Int>())
        runCurrent(); assertEquals(listOf(7),vm.state.value.data)
        fail=true;vm.refresh(true);runCurrent()
        assertEquals(listOf(7),vm.state.value.data)
        assertEquals("fixture offline",vm.state.value.error)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun clearingOwnerCancelsWithoutPublishingLateResponse() = runTest {
        var cancelled=false
        val ready=CompletableDeferred<List<Int>>()
        val vm=CanvasResourceViewModel(CanvasResourceRepository {
            try { ready.await() } finally { cancelled=true }
        },listOf(9))
        val store=ViewModelStore();store.put("fixture",vm)
        runCurrent();store.clear();runCurrent()
        ready.complete(listOf(4));runCurrent()
        assertTrue(cancelled)
        assertEquals(listOf(9),vm.state.value.data)
        assertNull(vm.state.value.error)
    }

    @Test fun hubPartialReadCannotPersistGlobalSnapshotOrClearAnotherFlowFailure() = runTest {
        val cache=MemoryCanvasCache()
        val previous=CanvasTodoItem(type="submitting",courseId=1,assignment=CanvasAssignment(id=10,name="Old"))
        cache.snapshot=listOf(previous)
        val snapshot=CanvasTodoSnapshot(listOf(previous),listOf(CanvasCourse(id=1,name="Fixture")),
            mapOf(1L to listOf(previous)),emptySet())
        val backingCache=cache
        val repo=object : CanvasHubRepository {
            override val cache=backingCache
            override fun requireSession() {}
            override suspend fun courses()=snapshot.courses
            override suspend fun conversations(): List<CanvasConversation> = throw IOException("fixture")
            override suspend fun notifications()=emptyList<CanvasNotificationItem>()
            override suspend fun groups()=emptyList<CanvasGroup>()
            override suspend fun todos(previous: List<CanvasTodoItem>)=
                RefreshResult.Partial(snapshot,listOf(RefreshIssue("Fixture",IOException())))
        }
        val vm=CanvasHubViewModel("fixture","synthetic",repo)
        runCurrent()
        assertEquals(0,cache.writes)
        assertEquals(123L,vm.state.value.lastUpdatedMillis)
        assertEquals(listOf(previous),vm.state.value.items)
        assertTrue(vm.state.value.isOffline)
        assertTrue(vm.state.value.refreshFailures.keys.containsAll(listOf("todo","conversations")))
    }

    @Test fun repositoryRejectsStaleSessionBeforeRequestAndBeforePublishingResponse() = runTest {
        MockWebServer().use { server ->
            val api=CanvasPagedApi(Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(CanvasApiService::class.java),
                server.url("/"))
            val cache=MemoryCanvasCache()
            val rejected=DefaultCanvasRepository("fixture","synthetic",api,cache) { false }
            try { rejected.courses(); fail("Stale owner") } catch (_: CancellationException) { }
            assertEquals(0,server.requestCount)
            server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("[]"))
            var validations=0
            val changed=DefaultCanvasRepository("fixture","synthetic",api,cache) { ++validations == 1 }
            try { changed.courses(); fail("Changed owner") } catch (_: CancellationException) { }
            assertEquals(1,server.requestCount)
            assertEquals(0,cache.writes)
        }
    }

    @Test fun setupCoalescesValidationAndDeliversSavedTokenOnce() = runTest {
        var calls=0
        val ready=CompletableDeferred<CanvasUser>()
        val vm=CanvasSetupViewModel(CanvasSetupRepository { calls++; ready.await() })
        vm.validate("  synthetic  ");runCurrent()
        vm.validate("other");runCurrent()
        assertEquals(1,calls)
        ready.complete(CanvasUser(1,"Fixture"));runCurrent()
        assertEquals("synthetic",vm.state.value.savedToken)
        vm.consumeSavedToken()
        assertNull(vm.state.value.savedToken)
        assertFalse(vm.state.value.loading)
    }

    @Test fun zoomPartialRefreshRetainsOnlyFailedSourceData() = runTest {
        val meeting=CourseZoomMeeting(courseId=1,title="Fixture",joinUrl="https://fixture.invalid/",origin="MODULE")
        var partial=false
        val repo=object : CanvasZoomRepository {
            override suspend fun load()=if (partial) CanvasZoomSnapshot(failedSources=setOf("MODULE","TABS"))
                else CanvasZoomSnapshot(meetings=listOf(meeting),ltiUrl="https://fixture.invalid/lti")
            override fun save(meeting: CourseZoomMeeting) {}
            override fun delete() {}
        }
        val vm=CanvasZoomViewModel(repo);runCurrent()
        partial=true;vm.refresh();runCurrent()
        assertEquals(listOf(meeting),vm.state.value.data.meetings)
        assertEquals("https://fixture.invalid/lti",vm.state.value.data.ltiUrl)
        assertNotNull(vm.state.value.error)
    }

    @Test fun stateKeysSeparateAccountGenerationTokenAndResourceWithoutRawToken() {
        val token="synthetic secret"
        val key=canvasViewModelKey("modules:1","a","session1",token)
        assertFalse(key.contains(token))
        assertNotEquals(key,canvasViewModelKey("modules:1","b","session1",token))
        assertNotEquals(key,canvasViewModelKey("modules:1","a","session2",token))
        assertNotEquals(key,canvasViewModelKey("modules:1","a","session1","changed"))
        assertNotEquals(key,canvasViewModelKey("modules:2","a","session1",token))
    }
}

private class MemoryCanvasCache : CanvasCacheStore {
    var snapshot: List<CanvasTodoItem> = emptyList()
    var writes = 0
    override suspend fun guardar(user: String, items: List<CanvasTodoItem>): Unit { snapshot=items; writes++ }
    override suspend fun cargar(user: String): Pair<List<CanvasTodoItem>, Long?> { return snapshot to 123L }
    override suspend fun guardarConversaciones(user: String, convs: List<CanvasConversation>): Unit { Unit }
    override suspend fun cargarConversaciones(user: String): List<CanvasConversation> { return emptyList() }
    override suspend fun guardarCursos(user: String, courses: List<CanvasCourse>): Unit { Unit }
    override suspend fun cargarCursos(user: String): List<CanvasCourse> { return listOf(CanvasCourse(id=1,name="Fixture")) }
    override suspend fun guardarNotificaciones(user: String, notifs: List<CanvasNotificationItem>): Unit { Unit }
    override suspend fun cargarNotificaciones(user: String): List<CanvasNotificationItem> { return emptyList() }
    override suspend fun guardarGrupos(user: String, groups: List<CanvasGroup>): Unit { Unit }
    override suspend fun cargarGrupos(user: String): List<CanvasGroup> { return emptyList() }
    override suspend fun guardarPendientesCurso(user: String, courseId: Long, items: List<CanvasTodoItem>): Unit { Unit }
    override suspend fun cargarPendientesPorCurso(user: String): Map<Long, List<CanvasTodoItem>> { return emptyMap() }
    override suspend fun limpiar(user: String): Unit { Unit }
}
