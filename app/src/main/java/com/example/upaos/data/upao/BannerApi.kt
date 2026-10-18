package com.example.upaos.data.upao

import android.content.Context
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.DetalleCursoResponse
import com.example.upaos.data.model.SubComponente
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class BannerApi(private val context: Context) {

    private val client = UpaoHttpClient.okHttpClient
    private val tokenManager = TokenManager(context)
    private val gson = Gson()
    private val semaphore = Semaphore(4)

    private val ssbBaseUrl = "https://ssb.upao.edu.pe/StudentSelfService/ssb/studentGrades"
    private val componentBaseUrl = "https://ssb.upao.edu.pe/StudentSelfService/componentDetails"

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
                        response = client.newCall(requestBuilder()).execute()
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
        val result = mutableListOf<JsonObject>()

        try {
            val parsed = JsonParser.parseString(jsonStr)
            val dataArray = when {
                parsed.isJsonArray -> parsed.asJsonArray
                parsed.isJsonObject && parsed.asJsonObject.has("data") -> parsed.asJsonObject.getAsJsonArray("data")
                else -> null
            }

            dataArray?.forEach { elem ->
                if (elem.isJsonObject) result.add(elem.asJsonObject)
            }
        } catch (_: Exception) {}

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
}
