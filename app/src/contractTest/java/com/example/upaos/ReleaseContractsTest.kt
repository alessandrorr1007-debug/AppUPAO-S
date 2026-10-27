package com.example.upaos

import com.example.upaos.data.api.CanvasApiService
import com.example.upaos.data.api.CanvasPagedApi
import com.example.upaos.data.local.CuentaGuardada
import com.example.upaos.data.agenda.AgendaItem
import com.example.upaos.data.model.*
import com.example.upaos.data.upao.BannerParsers
import com.example.upaos.data.upao.UpaoAuth
import com.example.upaos.util.GitHubRelease
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.junit.Assert.*
import org.junit.Test

/** Shared JVM/device contracts. On device, select release explicitly to exercise optimized code.
 * No activity is started and every HTTP request is answered by the in-process fixture interceptor.
 */
class ReleaseContractsTest {
    private val gson=Gson()

    @Test fun loginFormAndPasswordContractRemainIntact() {
        val parsed=BannerParsers.parseLoginForm(
            "<form action='/login'><input name='csrf' value='fixture'></form>","https://fixture.invalid/")
        assertEquals("fixture",parsed.hiddenInputs["csrf"])
        val form=UpaoAuth.buildLoginForm("fixture"," fixture secret ",parsed.hiddenInputs)
        val password=(0 until form.size).first { form.name(it)=="nip" }
        assertEquals(" fixture secret ",form.value(password))
    }

    @Test fun gradesAndNestedCreditCacheRoundTrip() {
        val json="""{"periodo":"202710","cursos":[{"courseReferenceNumber":"123","courseTitle":"Fixture","nota_actual":15,"raw_banner":{"hoursAttempted":4}}]}"""
        val first=gson.fromJson(json,GradesResponse::class.java)
        val restored=gson.fromJson(gson.toJson(first),GradesResponse::class.java)
        assertEquals("202710",restored.periodo)
        assertEquals("123",restored.cursos.single().courseReferenceNumber)
        assertEquals("Fixture",restored.cursos.single().courseTitle)
        assertEquals(4.0,(restored.cursos.single().rawBanner!!["hoursAttempted"] as Number).toDouble(),0.0)
    }

    @Test fun scheduleAndAttendanceNestedModelsRoundTrip() {
        val schedule=gson.fromJson(
            """{"success":true,"periodo":"202710","cursos":[{"crn":"123","nombre":"Fixture","bloques":[{"dia":0,"hora_inicio":"07:00","hora_fin":"08:00"}]}]}""",
            HorarioResponse::class.java)
        val restored=gson.fromJson(gson.toJson(schedule),HorarioResponse::class.java)
        assertEquals("Fixture",restored.listaCursos.single().displayNombre)
        assertEquals(1,restored.listaCursos.single().bloques.size)
        val attendance=gson.fromJson(
            """{"success":true,"asistencia":[{"crn":"123","materia":"Fixture","faltas":2,"componentes":[{"faltas":1}]}]}""",
            AsistenciaResponse::class.java)
        assertEquals(2,attendance.asistencia.single().faltas)
        assertEquals(1,attendance.asistencia.single().componentes.single().faltas)
    }

    @Test fun canvasRetrofitSuspendSignaturesAndPaginationWorkWithoutNetwork() = runBlocking {
        var requests=0
        val origin="https://fixture.invalid/".toHttpUrl()
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            val request=chain.request()
            check(request.url.host=="fixture.invalid")
            check(request.header("Authorization")=="Bearer synthetic")
            requests++
            val second=request.url.queryParameter("cursor")=="next"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .also { if (!second) it.header("Link","</api/v1/courses?cursor=next>; rel=next") }
                .body((if (second) """[{"id":2,"name":"B","course_code":"CODE-B"}]""" else
                    """[{"id":1,"name":"A","course_code":"CODE-A"}]""").toResponseBody()).build()
        }.build()
        val raw=Retrofit.Builder().baseUrl(origin).client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(CanvasApiService::class.java)
        val courses=CanvasPagedApi(raw,origin).getActiveCourses("Bearer synthetic").body()!!
        assertEquals(listOf("A","B"),courses.map { it.name })
        assertEquals("CODE-B",courses.last().courseCode)
        assertEquals(2,requests)
    }

    @Test fun savedAccountsAndAgendaKeepLegacyFieldNamesAndGenericTypes() {
        val accountsType=object : TypeToken<List<CuentaGuardada>>() {}.type
        val accounts: List<CuentaGuardada> = gson.fromJson(
            """[{"usuario":"fixture","password":"synthetic","nombre":"Fixture"}]""",accountsType)
        val serialized=gson.toJson(accounts)
        assertTrue(serialized.contains("\"usuario\""))
        assertTrue(serialized.contains("\"password\""))
        assertEquals("fixture",gson.fromJson<List<CuentaGuardada>>(serialized,accountsType).single().usuario)
        val agendaType=object : TypeToken<List<AgendaItem>>() {}.type
        val agenda: List<AgendaItem> = gson.fromJson(
            """[{"id":1,"courseId":2,"course":"Fixture","title":"Task","submitted":false,"graded":false}]""",agendaType)
        assertEquals("2:1",gson.fromJson<List<AgendaItem>>(gson.toJson(agenda),agendaType).single().key)
    }

    @Test fun updaterReleaseAndAssetsRetainSerializedNames() {
        val release=gson.fromJson(
            """{"tag_name":"v9.0.0","name":"Fixture","html_url":"https://fixture.invalid/release","assets":[{"name":"UPAO-S.apk","browser_download_url":"https://fixture.invalid/app.apk","size":123}]}""",
            GitHubRelease::class.java)
        val restored=gson.fromJson(gson.toJson(release),GitHubRelease::class.java)
        assertEquals("v9.0.0",restored.tagName)
        assertEquals("https://fixture.invalid/app.apk",restored.assets.single().downloadUrl)
        assertEquals(123L,restored.assets.single().size)
    }
}
