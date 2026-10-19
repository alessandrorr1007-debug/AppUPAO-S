package com.example.upaos.data.upao

import android.content.Context
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.AsistenciaComponente
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.AsistenciaResponse
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.DetalleCursoResponse
import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.data.model.SubComponente
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.Calendar
import kotlin.math.roundToInt

class BannerApi(private val context: Context) {

    private val client = UpaoHttpClient.okHttpClient
    private val tokenManager = TokenManager(context)
    private val gson = Gson()
    private val semaphore = Semaphore(4)

    private val ssbBaseUrl = "https://ssb.upao.edu.pe/StudentSelfService/ssb/studentGrades"
    private val componentBaseUrl = "https://ssb.upao.edu.pe/StudentSelfService/componentDetails"
    private val inscripcionBaseUrl = "https://inscripcion.upao.edu.pe/StudentRegistrationSsb/ssb"
    private val regHistoryUrl = "$inscripcionBaseUrl/registrationHistory/registrationHistory"
    private val resetRegistrationsUrl = "$inscripcionBaseUrl/registrationHistory/reset"
    private val meetingInfoUrl = "$inscripcionBaseUrl/classRegistration/getMeetingInformationForRegistrations"
    private val registrationEventsUrl = "$inscripcionBaseUrl/classRegistration/getRegistrationEvents"
    private val attendanceBaseUrl = "https://ssb.upao.edu.pe/StudentSelfService/ssb/studentAttendanceTracking"

    /**
     * Ejecuta una petición HTTP a la UPAO con protección de re-login automático.
     * Si la petición devuelve 401, redirige a login SSO o devuelve HTML de login,
     * refresca la sesión con TokenManager y reintenta exactamente 1 vez.
     */
    private suspend fun executeWithAuth(requestBuilder: () -> Request): Response = withContext(Dispatchers.IO) {
        semaphore.withPermit {
            var response = client.newCall(requestBuilder()).execute()

            if (isSessionExpired(response)) {
                response.close()
                val user = tokenManager.getSavedUser()
                val pass = tokenManager.getSavedPass()

                if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                    val loginRes = UpaoAuth.ensureSession(user, pass)
                    if (loginRes.success) {
                        val nextReq = requestBuilder()
                        if (nextReq.url.host.contains("inscripcion.upao.edu.pe")) {
                            UpaoAuth.ensureInscriptionSession()
                        }
                        response = client.newCall(nextReq).execute()
                    } else {
                        throw UpaoSessionExpiredException()
                    }
                } else {
                    throw UpaoSessionExpiredException()
                }
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code == 401 || code == 403) throw UpaoSessionExpiredException()
                throw UpaoServiceUnavailableException("Error del servidor UPAO (HTTP $code)")
            }

            response
        }
    }

    private fun isSessionExpired(response: Response): Boolean {
        if (response.code == 401 || response.code == 403) return true
        val url = response.request.url.toString()
        if (url.contains("upaosso.upao.edu.pe") || url.contains("/Account/Login")) return true
        val contentType = response.header("Content-Type").orEmpty().lowercase()
        if (contentType.contains("text/html")) {
            val preview = runCatching { response.peekBody(1024).string() }.getOrNull().orEmpty().lowercase()
            if (preview.contains("id_usuario") || preview.contains("__requestverificationtoken")) {
                return true
            }
        }
        return false
    }

    suspend fun getPeriodos(): List<String> = withContext(Dispatchers.IO) {
        val url = "$ssbBaseUrl/term?filter=&page=1&max=50"
        val response = executeWithAuth {
            Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ssbBaseUrl)
                .get()
                .build()
        }

        val jsonStr = response.body?.string().orEmpty()
        val periodos = mutableListOf<String>()

        try {
            val parsed = JsonParser.parseString(jsonStr)
            val items = when {
                parsed.isJsonArray -> parsed.asJsonArray
                parsed.isJsonObject && parsed.asJsonObject.has("items") -> parsed.asJsonObject.getAsJsonArray("items")
                parsed.isJsonObject && parsed.asJsonObject.has("data") -> parsed.asJsonObject.getAsJsonArray("data")
                else -> null
            }

            items?.forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val code = obj.get("code")?.asStringOrNull()
                        ?: obj.get("termCode")?.asStringOrNull()
                        ?: obj.get("id")?.asStringOrNull()
                        ?: obj.get("term")?.asStringOrNull()

                    if (!code.isNullOrBlank() && code.trim() != "-1") {
                        periodos.add(code.trim())
                    }
                }
            }
        } catch (_: Exception) {}

        periodos
    }

    suspend fun getNiveles(term: String): List<String> = withContext(Dispatchers.IO) {
        val url = "$ssbBaseUrl/level?filter=&term=$term"
        val response = executeWithAuth {
            Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ssbBaseUrl)
                .get()
                .build()
        }

        val jsonStr = response.body?.string().orEmpty()
        val niveles = mutableListOf<String>()

        try {
            val parsed = JsonParser.parseString(jsonStr)
            val items = when {
                parsed.isJsonArray -> parsed.asJsonArray
                parsed.isJsonObject && parsed.asJsonObject.has("items") -> parsed.asJsonObject.getAsJsonArray("items")
                parsed.isJsonObject && parsed.asJsonObject.has("data") -> parsed.asJsonObject.getAsJsonArray("data")
                else -> null
            }

            items?.forEach { elem ->
                if (elem.isJsonObject) {
                    val obj = elem.asJsonObject
                    val code = obj.get("code")?.asStringOrNull()
                        ?: obj.get("levelCode")?.asStringOrNull()
                        ?: obj.get("id")?.asStringOrNull()

                    if (!code.isNullOrBlank() && code.trim() != "-1") {
                        niveles.add(code.trim())
                    }
                }
            }
        } catch (_: Exception) {}

        if (niveles.isEmpty()) listOf("UG") else niveles
    }

    suspend fun getCourses(term: String, level: String = "UG"): List<CourseGrade> = withContext(Dispatchers.IO) {
        val urlWithLevel = "$ssbBaseUrl/courses?termCode=$term&levelCode=$level&filterText=&pageOffset=0&pageMaxSize=50&sortColumn=-1&sortDirection=-1"
        var items = fetchCoursesJson(urlWithLevel)

        if (items.isEmpty()) {
            val urlFallback = "$ssbBaseUrl/courses?termCode=$term&filterText=&pageOffset=0&pageMaxSize=50&sortColumn=-1&sortDirection=-1"
            items = fetchCoursesJson(urlFallback)
        }

        items.map { parseCourseGrade(it) }
    }

    private suspend fun fetchCoursesJson(url: String): List<JsonObject> {
        val response = executeWithAuth {
            Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", ssbBaseUrl)
                .get()
                .build()
        }

        val jsonStr = response.body?.string().orEmpty()
        val parsed = try {
            JsonParser.parseString(jsonStr)
        } catch (e: Exception) {
            throw UpaoUnexpectedFormatException("Error de sintaxis en JSON de cursos", cause = e)
        }

        val dataArray = when {
            parsed.isJsonArray -> parsed.asJsonArray
            parsed.isJsonObject && parsed.asJsonObject.has("data") && parsed.asJsonObject.get("data").isJsonArray ->
                parsed.asJsonObject.getAsJsonArray("data")
            parsed.isJsonObject && parsed.asJsonObject.has("items") && parsed.asJsonObject.get("items").isJsonArray ->
                parsed.asJsonObject.getAsJsonArray("items")
            else -> throw UpaoUnexpectedFormatException("El JSON de cursos no contiene el arreglo esperado ('data' o raíz)")
        }

        val result = mutableListOf<JsonObject>()
        dataArray.forEach { elem ->
            if (elem.isJsonObject) result.add(elem.asJsonObject)
        }
        return result
    }

    private fun parseCourseGrade(obj: JsonObject): CourseGrade {
        val crn = obj.get("courseReferenceNumber")?.asStringOrNull()
            ?: obj.get("crn")?.asStringOrNull()
            ?: obj.get("id")?.asStringOrNull()

        val title = obj.get("courseTitle")?.asStringOrNull()
            ?: obj.get("subjectDescription")?.asStringOrNull()
            ?: obj.get("subjectTitle")?.asStringOrNull()
            ?: obj.get("title")?.asStringOrNull()
            ?: "Curso Matriculado"

        val rawMap = runCatching {
            gson.fromJson<Map<String, Any?>>(obj, Map::class.java)
        }.getOrNull()

        return CourseGrade(
            nombre = title,
            courseTitle = title,
            subjectDescription = obj.get("subjectDescription")?.asStringOrNull(),
            crn = crn,
            courseReferenceNumber = crn,
            rawBanner = rawMap
        )
    }

    suspend fun getCourseGradeDetail(term: String, crn: String): DetalleCursoResponse = withContext(Dispatchers.IO) {
        val url = "$componentBaseUrl/componentDetails?selectedTerm=$term&selectedCrn=$crn&filterText=&pageOffset=0&pageMaxSize=20&sortColumn=name&sortDirection=asc"
        val response = executeWithAuth {
            Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$componentBaseUrl/componentDetails")
                .get()
                .build()
        }

        val jsonStr = response.body?.string().orEmpty()
        val componentes = mutableListOf<ComponenteDetalle>()

        try {
            val parsed = JsonParser.parseString(jsonStr)
            val items = when {
                parsed.isJsonArray -> parsed.asJsonArray
                parsed.isJsonObject && parsed.asJsonObject.has("data") -> parsed.asJsonObject.getAsJsonArray("data")
                else -> null
            }

            items?.forEach { elem ->
                if (elem.isJsonObject) {
                    val comp = parseComponente(elem.asJsonObject)
                    val compWithSubs = if (comp.hasSubComponents && comp.componentId != null) {
                        val subs = getSubcomponentDetails(term, crn, comp.componentId)
                        comp.copy(subcomponentes = subs)
                    } else {
                        comp
                    }
                    componentes.add(compWithSubs)
                }
            }
        } catch (_: Exception) {}

        val (proyectada, pendientes) = Calculators.calcularNotaProyectada(componentes)

        DetalleCursoResponse(
            success = true,
            totalCount = componentes.size,
            detalles = componentes,
            notaProyectada = proyectada,
            pesosPendientes = pendientes
        )
    }

    suspend fun getSubcomponentDetails(term: String, crn: String, componentId: Long): List<SubComponente> = withContext(Dispatchers.IO) {
        val url = "$componentBaseUrl/subComponentDetails?selectedTerm=$term&selectedCrn=$crn&selectedComponentId=$componentId&filterText=&pageOffset=0&pageMaxSize=20&sortColumn=name&sortDirection=asc"
        val response = executeWithAuth {
            Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$componentBaseUrl/componentDetails")
                .get()
                .build()
        }

        val jsonStr = response.body?.string().orEmpty()
        val subcomponentes = mutableListOf<SubComponente>()

        try {
            val parsed = JsonParser.parseString(jsonStr)
            val items = when {
                parsed.isJsonArray -> parsed.asJsonArray
                parsed.isJsonObject && parsed.asJsonObject.has("data") -> parsed.asJsonObject.getAsJsonArray("data")
                else -> null
            }

            items?.forEach { elem ->
                if (elem.isJsonObject) {
                    subcomponentes.add(parseSubComponente(elem.asJsonObject))
                }
            }
        } catch (_: Exception) {}

        subcomponentes
    }

    private fun parseComponente(obj: JsonObject): ComponenteDetalle {
        val componentId = obj.get("componentId")?.asLongOrNull()
            ?: obj.get("componentID")?.asLongOrNull()
            ?: obj.get("id")?.asLongOrNull()

        val nombre = obj.get("description")?.asStringOrNull()
            ?: obj.get("componentDescription")?.asStringOrNull()
            ?: obj.get("name")?.asStringOrNull()
            ?: obj.get("title")?.asStringOrNull()
            ?: "Componente"

        val codigo = obj.get("name")?.asStringOrNull()
            ?: obj.get("componentName")?.asStringOrNull()
            ?: obj.get("componentCode")?.asStringOrNull()

        val peso = obj.get("weight")?.asDoubleOrNull()
            ?: obj.get("weightPercent")?.asDoubleOrNull()
            ?: obj.get("percentWeight")?.asDoubleOrNull()
            ?: obj.get("gradeWeight")?.asDoubleOrNull()

        val porcentajeLogrado = obj.get("percentage")?.asDoubleOrNull()
            ?: obj.get("percentAchieved")?.asDoubleOrNull()

        val gradeVal = obj.get("grade")?.asStringOrNull()
            ?: obj.get("finalGrade")?.asStringOrNull()
            ?: obj.get("gradeEarned")?.asStringOrNull()

        val scoreVal = obj.get("score")?.asDoubleOrNull()
            ?: obj.get("pointsEarned")?.asDoubleOrNull()
            ?: obj.get("earnedPoints")?.asDoubleOrNull()

        val puntajeObtenido: Any? = if (scoreVal != null) {
            scoreVal
        } else if (!gradeVal.isNullOrBlank() && gradeVal.toDoubleOrNull() == null) {
            gradeVal
        } else {
            gradeVal?.toDoubleOrNull() ?: gradeVal
        }

        val sobre = obj.get("totalScore")?.asDoubleOrNull()
            ?: obj.get("possible")?.asDoubleOrNull()
            ?: obj.get("maxPoints")?.asDoubleOrNull()
            ?: obj.get("pointPossible")?.asDoubleOrNull()

        val hasSubRaw = obj.get("hasSubComponents")?.asStringOrNull()
            ?: obj.get("hasSubcomponents")?.asStringOrNull()
            ?: obj.get("hasSubComponent")?.asStringOrNull()

        val subCount = obj.get("subComponentCount")?.asIntOrNull() ?: 0
        var hasSub = obj.get("hasSubComponents")?.asBooleanOrNull() ?: false

        if (!hasSub && !hasSubRaw.isNullOrBlank()) {
            val upper = hasSubRaw.uppercase()
            hasSub = upper !in listOf("N", "0", "FALSE", "NO", "NONE")
        }
        if (subCount > 0) hasSub = true

        return ComponenteDetalle(
            nombre = nombre,
            description = nombre,
            componente = nombre,
            codigo = codigo,
            peso = peso,
            porcentajeLogrado = porcentajeLogrado,
            puntajeObtenido = puntajeObtenido,
            puntajeSobre = sobre,
            score = scoreVal,
            grade = puntajeObtenido,
            nota = puntajeObtenido,
            gradeOficial = gradeVal,
            componentId = componentId,
            hasSubComponents = hasSub
        )
    }

    private fun parseSubComponente(obj: JsonObject): SubComponente {
        val nombre = obj.get("description")?.asStringOrNull()
            ?: obj.get("componentDescription")?.asStringOrNull()
            ?: obj.get("name")?.asStringOrNull()
            ?: obj.get("title")?.asStringOrNull()
            ?: "Sub-componente"

        val codigo = obj.get("name")?.asStringOrNull()
            ?: obj.get("componentName")?.asStringOrNull()

        val peso = obj.get("weight")?.asDoubleOrNull()
            ?: obj.get("weightPercent")?.asDoubleOrNull()
            ?: obj.get("percentage")?.asDoubleOrNull()

        val scoreVal = obj.get("score")?.asDoubleOrNull()
            ?: obj.get("pointsEarned")?.asDoubleOrNull()

        val gradeVal = obj.get("grade")?.asStringOrNull()
            ?: obj.get("gradeEarned")?.asStringOrNull()

        val obtenido: Any? = scoreVal ?: gradeVal?.toDoubleOrNull() ?: gradeVal

        val sobre = obj.get("totalScore")?.asDoubleOrNull()
            ?: obj.get("possible")?.asDoubleOrNull()
            ?: obj.get("maxPoints")?.asDoubleOrNull()

        return SubComponente(
            nombre = nombre,
            description = nombre,
            componente = nombre,
            codigo = codigo,
            peso = peso,
            porcentajeLogrado = obj.get("percentage")?.asDoubleOrNull(),
            puntajeObtenido = obtenido,
            puntajeSobre = sobre,
            grade = obtenido,
            nota = obtenido,
            score = scoreVal
        )
    }

    private fun JsonElement.asStringOrNull(): String? =
        if (this.isJsonPrimitive && this.asJsonPrimitive.isString) this.asString else if (this.isJsonPrimitive) this.asString else null

    private fun JsonElement.asDoubleOrNull(): Double? =
        if (this.isJsonPrimitive && this.asJsonPrimitive.isNumber) this.asDouble else this.asStringOrNull()?.toDoubleOrNull()

    private fun JsonElement.asLongOrNull(): Long? =
        if (this.isJsonPrimitive && this.asJsonPrimitive.isNumber) this.asLong else this.asStringOrNull()?.toLongOrNull()

    private fun JsonElement.asIntOrNull(): Int? =
        if (this.isJsonPrimitive && this.asJsonPrimitive.isNumber) this.asInt else this.asStringOrNull()?.toIntOrNull()

    private fun JsonElement.asBooleanOrNull(): Boolean? =
        if (this.isJsonPrimitive && this.asJsonPrimitive.isBoolean) this.asBoolean else this.asStringOrNull()?.toBooleanStrictOrNull()

    private fun JsonElement.asJsonArrayOrNull(): List<JsonElement>? =
        if (this.isJsonArray) this.asJsonArray.toList() else null

    /**
     * Consulta el horario del periodo desde inscripcion.upao.edu.pe enriquecido con aulas y docentes.
     */
    suspend fun getHorario(term: String): HorarioResponse = withContext(Dispatchers.IO) {
        try {
            UpaoAuth.ensureInscriptionSession()

            // 1. Visitar registrationHistory para inicializar estado en StudentRegistrationSsb
            val reqHistory = Request.Builder()
                .url(regHistoryUrl)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", regHistoryUrl)
                .get()
                .build()
            try {
                client.newCall(reqHistory).execute().close()
            } catch (_: Exception) {}

            // 2. GET registrationHistory/reset?term={term}
            val resetUrl = "$resetRegistrationsUrl?term=$term"
            val response = executeWithAuth {
                Request.Builder()
                    .url(resetUrl)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", regHistoryUrl)
                    .get()
                    .build()
            }

            val jsonStr = response.body?.string().orEmpty()
            val registros = BannerParsers.parseHorarioJson(jsonStr).toMutableList()

            val crns = registros.mapNotNull {
                it.get("courseReferenceNumber")?.asStringOrNull() ?: it.get("crn")?.asStringOrNull()
            }

            val meetingInfoMap = fetchMeetingInfoMap(term, crns)

            val cursos = if (registros.isNotEmpty()) {
                parseHorarioFromRegistros(registros, term, meetingInfoMap)
            } else {
                // Fallback a getRegistrationEvents si no hubo registros en reset
                val eventsUrl = "$registrationEventsUrl?termFilter=$term"
                val eventsResponse = executeWithAuth {
                    Request.Builder()
                        .url(eventsUrl)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", regHistoryUrl)
                        .get()
                        .build()
                }
                val eventsJson = eventsResponse.body?.string().orEmpty()
                val eventos = mutableListOf<JsonObject>()
                try {
                    val parsedEvents = JsonParser.parseString(eventsJson)
                    if (parsedEvents.isJsonArray) {
                        for (elem in parsedEvents.asJsonArray) {
                            if (elem.isJsonObject) eventos.add(elem.asJsonObject)
                        }
                    }
                } catch (_: Exception) {}

                val finalMeetingMap = if (meetingInfoMap.isEmpty()) {
                    val eventCrns = eventos.mapNotNull { it.get("crn")?.asStringOrNull() }
                    fetchMeetingInfoMap(term, eventCrns)
                } else {
                    meetingInfoMap
                }

                parseHorarioFromEvents(eventos, term, finalMeetingMap)
            }

            HorarioResponse(
                success = true,
                periodo = term,
                totalCursos = cursos.size,
                totalBloques = cursos.sumOf { it.bloques.size },
                cursos = cursos
            )
        } catch (e: UpaoException) {
            throw e
        } catch (_: Exception) {
            HorarioResponse(
                success = false,
                periodo = term,
                totalCursos = 0,
                totalBloques = 0,
                cursos = emptyList()
            )
        }
    }

    private suspend fun fetchMeetingInfoMap(term: String, crns: List<String>): Map<String, JsonObject> = withContext(Dispatchers.IO) {
        val meetingMap = mutableMapOf<String, JsonObject>()
        val classRegReferer = "$inscripcionBaseUrl/classRegistration/classRegistration"
        val bulkUrl = "$meetingInfoUrl?term=$term"

        try {
            val response = executeWithAuth {
                Request.Builder()
                    .url(bulkUrl)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", classRegReferer)
                    .get()
                    .build()
            }
            val json = response.body?.string().orEmpty()
            val parsed = JsonParser.parseString(json)
            val items: List<JsonObject> = when {
                parsed.isJsonArray -> parsed.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                parsed.isJsonObject -> {
                    val data = parsed.asJsonObject.getAsJsonArray("data")
                        ?: parsed.asJsonObject.getAsJsonArray("items")
                    data?.mapNotNull { if (it.isJsonObject) it.asJsonObject else null } ?: emptyList()
                }
                else -> emptyList()
            }

            for (item in items) {
                val c = item.get("courseReferenceNumber")?.asStringOrNull()
                    ?: item.get("crn")?.asStringOrNull()
                if (c != null) {
                    meetingMap[c] = item
                }
            }
        } catch (_: Exception) {}

        val faltantes = crns.filter { it !in meetingMap }
        if (faltantes.isNotEmpty()) {
            coroutineScope {
                faltantes.map { crn ->
                    async {
                        try {
                            val url = "$meetingInfoUrl?term=$term&courseReferenceNumber=$crn"
                            val res = executeWithAuth {
                                Request.Builder()
                                    .url(url)
                                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                                    .header("X-Requested-With", "XMLHttpRequest")
                                    .header("Referer", classRegReferer)
                                    .get()
                                    .build()
                            }
                            val j = res.body?.string().orEmpty()
                            val p = JsonParser.parseString(j)
                            val item = when {
                                p.isJsonArray && p.asJsonArray.size() > 0 && p.asJsonArray[0].isJsonObject -> p.asJsonArray[0].asJsonObject
                                p.isJsonObject -> p.asJsonObject
                                else -> null
                            }
                            if (item != null) {
                                synchronized(meetingMap) {
                                    meetingMap[crn] = item
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }.awaitAll()
            }
        }

        meetingMap
    }

    private fun parseHorarioFromRegistros(
        registros: List<JsonObject>,
        term: String,
        meetingInfoMap: Map<String, JsonObject>
    ): List<HorarioCurso> {
        val cursosMap = mutableMapOf<String, MutableHorarioCurso>()

        for (reg in registros) {
            val crn = reg.get("courseReferenceNumber")?.asStringOrNull()
                ?: reg.get("crn")?.asStringOrNull()
            val subject = reg.get("subject")?.asStringOrNull()
            val courseNumber = reg.get("courseNumber")?.asStringOrNull()
            val seccion = reg.get("sequenceNumber")?.asStringOrNull()
                ?: reg.get("section")?.asStringOrNull()

            if (crn == null && subject == null) continue

            val clave = if (subject != null && courseNumber != null) {
                "$subject|$courseNumber"
            } else {
                "crn:$crn"
            }

            val infoExtra = if (crn != null) meetingInfoMap[crn] else null

            val profCurso = extraerProfesor(infoExtra?.get("faculty"))
                ?: extraerProfesor(reg.get("faculty"))

            val titleRaw = reg.get("courseTitle")?.asStringOrNull()
                ?: reg.get("title")?.asStringOrNull()
                ?: "Curso"
            val nombreCurso = cleanHtmlStr(titleRaw) ?: "Curso"

            val curso = cursosMap.getOrPut(clave) {
                MutableHorarioCurso(
                    crn = crn ?: clave,
                    codigoMateria = subject,
                    numeroCurso = courseNumber,
                    nombre = nombreCurso,
                    seccion = seccion,
                    docente = profCurso,
                    profesor = profCurso,
                    nombreDocente = profCurso,
                    nombreProfesor = profCurso,
                    bloques = mutableListOf()
                )
            }

            if (profCurso != null && curso.docente == null) {
                curso.docente = profCurso
                curso.profesor = profCurso
                curso.nombreDocente = profCurso
                curso.nombreProfesor = profCurso
            }

            val meetingTimes = reg.get("meetingTimes")?.asJsonArrayOrNull()
                ?: infoExtra?.get("meetingTimes")?.asJsonArrayOrNull()
                ?: emptyList()

            val meetingsFaculty = reg.get("meetingsFaculty")?.asJsonArrayOrNull()
                ?: infoExtra?.get("meetingsFaculty")?.asJsonArrayOrNull()
                ?: emptyList()

            for ((idxMt, mtElement) in meetingTimes.withIndex()) {
                if (!mtElement.isJsonObject) continue
                val mt = mtElement.asJsonObject

                val beginTimeRaw = mt.get("beginTime")?.asStringOrNull()
                val endTimeRaw = mt.get("endTime")?.asStringOrNull()
                val (horaInicio24, horaInicio12) = ScheduleCalculator.convertirHora(beginTimeRaw)
                val (horaFin24, horaFin12) = ScheduleCalculator.convertirHora(endTimeRaw)

                if (beginTimeRaw.isNullOrBlank() || horaInicio24.isBlank()) continue

                var profBloque = profCurso
                if (idxMt < meetingsFaculty.size && meetingsFaculty[idxMt].isJsonObject) {
                    val mfObj = meetingsFaculty[idxMt].asJsonObject
                    val mfFac = mfObj.get("faculty")
                    val mfProf = extraerProfesor(mfFac)
                    if (mfProf != null) {
                        profBloque = mfProf
                    }
                }

                val edificio = extraerEdificio(mt)
                val salon = extraerSalon(mt)
                val aula = ScheduleCalculator.formatoAula(edificio, salon)
                    ?: mt.get("location")?.asStringOrNull()
                val tipo = mt.get("meetingScheduleType")?.asStringOrNull()
                    ?: mt.get("scheduleTypeDescription")?.asStringOrNull()

                for ((idxDia, flag) in ScheduleCalculator.FLAG_DIAS.withIndex()) {
                    val diaActivo = mt.get(flag)?.asBooleanOrNull() ?: false
                    if (diaActivo) {
                        curso.bloques.add(
                            HorarioBloque(
                                dia = idxDia,
                                diaNombre = ScheduleCalculator.DIAS_NOMBRES[idxDia],
                                horaInicio = horaInicio24,
                                horaFin = horaFin24,
                                horaInicio12h = horaInicio12,
                                horaFin12h = horaFin12,
                                aula = aula,
                                docente = profBloque,
                                profesor = profBloque,
                                nombreDocente = profBloque,
                                nombreProfesor = profBloque,
                                tipo = tipo,
                                tipoComponente = tipo,
                                crn = crn,
                                nrc = crn,
                                seccion = seccion
                            )
                        )
                    }
                }
            }
        }

        return cursosMap.values.map { mutableCurso ->
            val fusionados = ScheduleCalculator.fusionarBloques(mutableCurso.bloques)
            HorarioCurso(
                crn = mutableCurso.crn,
                codigoMateria = mutableCurso.codigoMateria,
                numeroCurso = mutableCurso.numeroCurso,
                nombre = mutableCurso.nombre,
                seccion = mutableCurso.seccion,
                docente = mutableCurso.docente,
                profesor = mutableCurso.profesor,
                nombreDocente = mutableCurso.nombreDocente,
                nombreProfesor = mutableCurso.nombreProfesor,
                bloques = fusionados
            )
        }.sortedBy { it.displayNombre.lowercase() }
    }

    private fun parseHorarioFromEvents(
        eventos: List<JsonObject>,
        term: String,
        meetingInfoMap: Map<String, JsonObject>
    ): List<HorarioCurso> {
        val cursosMap = mutableMapOf<String, MutableHorarioCurso>()

        for (ev in eventos) {
            val crn = ev.get("crn")?.asStringOrNull()
                ?: ev.get("courseReferenceNumber")?.asStringOrNull()
                ?: ev.get("id")?.asStringOrNull()
                ?: continue

            val startStr = ev.get("start")?.asStringOrNull()
            val parsedStart = parseFechaHorario(startStr) ?: continue
            val (dia, horaInicio) = parsedStart

            val endStr = ev.get("end")?.asStringOrNull()
            val parsedEnd = parseFechaHorario(endStr)
            val horaFin = parsedEnd?.second

            val clave = crn
            val infoExtra = meetingInfoMap[clave]
            val profCurso = extraerProfesor(infoExtra?.get("faculty"))

            val titleRaw = ev.get("title")?.asStringOrNull()
                ?: ev.get("courseTitle")?.asStringOrNull()
                ?: "Curso"
            val nombre = cleanHtmlStr(titleRaw) ?: "Curso"

            val curso = cursosMap.getOrPut(clave) {
                MutableHorarioCurso(
                    crn = clave,
                    codigoMateria = ev.get("subject")?.asStringOrNull(),
                    numeroCurso = ev.get("courseNumber")?.asStringOrNull(),
                    nombre = nombre,
                    seccion = ev.get("section")?.asStringOrNull(),
                    docente = profCurso,
                    profesor = profCurso,
                    nombreDocente = profCurso,
                    nombreProfesor = profCurso,
                    bloques = mutableListOf()
                )
            }

            val edificio = extraerEdificio(ev) ?: extraerEdificio(infoExtra)
            val salon = extraerSalon(ev) ?: extraerSalon(infoExtra)
            val aula = aulaDeMeeting(ev) ?: aulaDeMeeting(infoExtra) ?: ev.get("location")?.asStringOrNull()

            val (horaInicio24, horaInicio12) = ScheduleCalculator.convertirHora(horaInicio.replace(":", ""))
            val (horaFin24, horaFin12) = if (horaFin != null) ScheduleCalculator.convertirHora(horaFin.replace(":", "")) else ("" to "")

            curso.bloques.add(
                HorarioBloque(
                    dia = dia,
                    diaNombre = ScheduleCalculator.DIAS_NOMBRES[dia],
                    horaInicio = horaInicio24.ifBlank { horaInicio },
                    horaFin = horaFin24.ifBlank { horaFin },
                    horaInicio12h = horaInicio12.ifBlank { horaInicio },
                    horaFin12h = horaFin12.ifBlank { horaFin },
                    aula = aula,
                    docente = profCurso,
                    profesor = profCurso,
                    nombreDocente = profCurso,
                    nombreProfesor = profCurso,
                    crn = crn,
                    nrc = crn
                )
            )
        }

        return cursosMap.values.map { mutableCurso ->
            val fusionados = ScheduleCalculator.fusionarBloques(mutableCurso.bloques)
            HorarioCurso(
                crn = mutableCurso.crn,
                codigoMateria = mutableCurso.codigoMateria,
                numeroCurso = mutableCurso.numeroCurso,
                nombre = mutableCurso.nombre,
                seccion = mutableCurso.seccion,
                docente = mutableCurso.docente,
                profesor = mutableCurso.profesor,
                nombreDocente = mutableCurso.nombreDocente,
                nombreProfesor = mutableCurso.nombreProfesor,
                bloques = fusionados
            )
        }.sortedBy { it.displayNombre.lowercase() }
    }

    private fun cleanHtmlStr(str: String?): String? {
        if (str.isNullOrBlank()) return null
        val unescaped = Parser.unescapeEntities(str, false)
        val cleaned = unescaped.replace("\\s+".toRegex(), " ").trim()
        return cleaned.ifBlank { null }
    }

    private fun extraerProfesor(facultyElement: JsonElement?): String? {
        if (facultyElement == null || facultyElement.isJsonNull) return null
        val nombres = mutableListOf<String>()
        val list = when {
            facultyElement.isJsonArray -> facultyElement.asJsonArray
            else -> null
        }
        if (list != null) {
            for (item in list) {
                if (item.isJsonObject) {
                    val obj = item.asJsonObject
                    val name = obj.get("displayName")?.asStringOrNull()
                        ?: obj.get("name")?.asStringOrNull()
                        ?: obj.get("instructorName")?.asStringOrNull()
                    val clean = cleanHtmlStr(name)
                    if (!clean.isNullOrBlank() && clean !in nombres) {
                        nombres.add(clean)
                    }
                } else if (item.isJsonPrimitive) {
                    val clean = cleanHtmlStr(item.asStringOrNull())
                    if (!clean.isNullOrBlank() && clean !in nombres) {
                        nombres.add(clean)
                    }
                }
            }
        } else if (facultyElement.isJsonObject) {
            val obj = facultyElement.asJsonObject
            val name = obj.get("displayName")?.asStringOrNull()
                ?: obj.get("name")?.asStringOrNull()
                ?: obj.get("instructorName")?.asStringOrNull()
            val clean = cleanHtmlStr(name)
            if (!clean.isNullOrBlank()) nombres.add(clean)
        } else if (facultyElement.isJsonPrimitive) {
            val clean = cleanHtmlStr(facultyElement.asStringOrNull())
            if (!clean.isNullOrBlank()) nombres.add(clean)
        }
        return if (nombres.isNotEmpty()) nombres.joinToString(", ") else null
    }

    private fun extraerEdificio(obj: JsonObject?): String? {
        if (obj == null) return null
        val bldg = obj.get("buildingDescription")?.asStringOrNull()
            ?: obj.get("building")?.asStringOrNull()
        return cleanHtmlStr(bldg)
    }

    private fun extraerSalon(obj: JsonObject?): String? {
        if (obj == null) return null
        val room = obj.get("room")?.asStringOrNull()
        return cleanHtmlStr(room)
    }

    private fun aulaDeMeeting(obj: JsonObject?): String? {
        if (obj == null) return null
        val ed = extraerEdificio(obj)
        val sal = extraerSalon(obj)
        return ScheduleCalculator.formatoAula(ed, sal)
    }

    private fun parseFechaHorario(fechaStr: String?): Pair<Int, String>? {
        if (fechaStr.isNullOrBlank()) return null
        val regex = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})")
        val match = regex.find(fechaStr) ?: return null
        val (y, m, d, hh, mm) = match.destructured
        val cal = Calendar.getInstance()
        cal.set(y.toInt(), m.toInt() - 1, d.toInt())
        val weekday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val hora = "$hh:$mm"
        return weekday to hora
    }

    /**
     * Consulta el registro completo de asistencias desde studentAttendanceTracking/getRegisteredSections.
     */
    suspend fun getAsistencia(pageMaxSize: Int = 50): AsistenciaResponse = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<JsonObject>()
        var totalCount: Int? = null
        var offset = 0

        try {
            while (true) {
                val url = "$attendanceBaseUrl/getRegisteredSections?filterText=&pageMaxSize=$pageMaxSize&pageOffset=$offset&sortColumn=courseReferenceNumber&sortDirection=asc"
                val response = executeWithAuth {
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", attendanceBaseUrl)
                        .get()
                        .build()
                }

                val jsonStr = response.body?.string().orEmpty()
                val (rawTotal, pageItems) = BannerParsers.parseAsistenciaJson(jsonStr)
                if (rawTotal != null) totalCount = rawTotal
                allItems.addAll(pageItems)

                if (pageItems.isEmpty()) break
                if (totalCount != null && allItems.size >= totalCount) break
                if (offset > 10000) break
                offset += pageMaxSize
            }

            val cursos = agruparAsistencia(allItems)
            val totalSecciones = allItems.size

            AsistenciaResponse(
                success = true,
                totalCount = cursos.size,
                totalSecciones = totalSecciones,
                asistencia = cursos
            )
        } catch (e: UpaoException) {
            throw e
        } catch (_: Exception) {
            AsistenciaResponse(
                success = false,
                totalCount = 0,
                totalSecciones = 0,
                asistencia = emptyList()
            )
        }
    }

    private fun agruparAsistencia(rawItems: List<JsonObject>): List<AsistenciaCurso> {
        data class GrupoKey(
            val periodo: String?,
            val codigoMateria: String?,
            val curso: String?,
            val nombreCurso: String?
        )

        val grupos = mutableMapOf<GrupoKey, MutableAsistenciaCurso>()
        val orden = mutableListOf<GrupoKey>()

        for (item in rawItems) {
            val crn = item.get("courseReferenceNumber")?.asStringOrNull()
                ?: item.get("crn")?.asStringOrNull()
            val cursoNum = item.get("courseNumber")?.asStringOrNull()
                ?: item.get("courseDisplayValue")?.asStringOrNull()
            val term = item.get("termCode")?.asStringOrNull()
            val subjectDesc = cleanHtmlStr(item.get("subjectDesc")?.asStringOrNull())
            val subjectCode = item.get("subjectCode")?.asStringOrNull()
            val sectionTitle = cleanHtmlStr(item.get("sectionTitle")?.asStringOrNull())
            val sequence = item.get("sequenceNumber")?.asStringOrNull()
            val missed = item.get("missed")?.asIntOrNull()
            val percentage = item.get("percentage")?.asDoubleOrNull()
            val schedule = item.get("schedule")
            val horarioDias = convertirHorarioDias(schedule)
            val (hora24, hora12) = ScheduleCalculator.convertirHora(item.get("time")?.asStringOrNull())
            val sectionMeetingId = item.get("sectionMeetingId")?.asLongOrNull()
            val docente = item.get("instructor")?.asStringOrNull()
                ?: item.get("instructorName")?.asStringOrNull()
                ?: extraerProfesor(item.get("faculty"))
            val aula = ScheduleCalculator.formatoAula(
                item.get("building")?.asStringOrNull(),
                item.get("room")?.asStringOrNull()
            )

            val key = GrupoKey(
                periodo = term,
                codigoMateria = subjectCode,
                curso = cursoNum,
                nombreCurso = sectionTitle
            )

            if (key !in grupos) {
                grupos[key] = MutableAsistenciaCurso(
                    crn = crn,
                    materia = subjectDesc,
                    codigoMateria = subjectCode,
                    nombreCurso = sectionTitle,
                    periodo = term,
                    horarioDias = horarioDias,
                    hora = hora24.ifBlank { null },
                    hora12h = hora12.ifBlank { null },
                    aula = aula,
                    docente = docente,
                    profesor = docente,
                    componentes = mutableListOf()
                )
                orden.add(key)
            }

            grupos[key]?.componentes?.add(
                AsistenciaComponente(
                    crn = crn,
                    seccion = sequence,
                    porcentaje = percentage,
                    faltas = missed,
                    horarioDias = horarioDias,
                    hora = hora24.ifBlank { null },
                    hora12h = hora12.ifBlank { null },
                    aula = aula,
                    docente = docente,
                    profesor = docente,
                    sectionMeetingId = sectionMeetingId
                )
            )
        }

        return orden.mapNotNull { key ->
            val g = grupos[key] ?: return@mapNotNull null
            val comps = g.componentes
            val pcts = comps.mapNotNull { it.porcentaje }
            val promPorcentaje = if (pcts.isNotEmpty()) {
                (pcts.average() * 10.0).roundToInt() / 10.0
            } else {
                null
            }
            val totalFaltas = comps.sumOf { it.faltas ?: 0 }

            AsistenciaCurso(
                crn = g.crn,
                materia = g.materia,
                codigoMateria = g.codigoMateria,
                nombreCurso = g.nombreCurso,
                seccion = comps.firstOrNull()?.seccion,
                periodo = g.periodo,
                faltas = totalFaltas,
                porcentaje = promPorcentaje,
                horarioDias = g.horarioDias,
                hora = g.hora,
                hora12h = g.hora12h,
                aula = g.aula,
                docente = g.docente,
                profesor = g.profesor,
                componentes = comps.toList(),
                totalSecciones = comps.size
            )
        }
    }

    private fun convertirHorarioDias(scheduleElement: JsonElement?): String {
        val dias = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        if (scheduleElement == null || !scheduleElement.isJsonArray) return "Sin horario registrado"
        val arr = scheduleElement.asJsonArray
        val activos = mutableListOf<String>()
        for ((idx, elem) in arr.withIndex()) {
            if (idx >= dias.size) break
            val isTrue = when {
                elem.isJsonPrimitive && elem.asJsonPrimitive.isBoolean -> elem.asBoolean
                elem.isJsonPrimitive && elem.asJsonPrimitive.isString -> {
                    val s = elem.asString.trim()
                    s.isNotBlank() && !s.equals("false", ignoreCase = true)
                }
                else -> false
            }
            if (isTrue) {
                activos.add(dias[idx])
            }
        }
        return if (activos.isNotEmpty()) activos.joinToString(", ") else "Sin horario registrado"
    }
}

private data class MutableHorarioCurso(
    val crn: String? = null,
    val codigoMateria: String? = null,
    val numeroCurso: String? = null,
    val nombre: String? = null,
    val seccion: String? = null,
    var docente: String? = null,
    var profesor: String? = null,
    var nombreDocente: String? = null,
    var nombreProfesor: String? = null,
    val bloques: MutableList<HorarioBloque> = mutableListOf()
)

private data class MutableAsistenciaCurso(
    val crn: String? = null,
    val materia: String? = null,
    val codigoMateria: String? = null,
    val nombreCurso: String? = null,
    val periodo: String? = null,
    val horarioDias: String? = null,
    val hora: String? = null,
    val hora12h: String? = null,
    val aula: String? = null,
    val docente: String? = null,
    val profesor: String? = null,
    val componentes: MutableList<AsistenciaComponente> = mutableListOf()
)
