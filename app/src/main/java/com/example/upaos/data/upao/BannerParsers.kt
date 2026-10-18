package com.example.upaos.data.upao

import com.example.upaos.data.model.CourseGrade
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup

data class LoginFormResult(
    val actionUrl: String,
    val hiddenInputs: Map<String, String>
)

object BannerParsers {

    private val gson = Gson()

    /**
     * Parsea el formulario de login SSO (WSO2 / Banner).
     * Lanza [UpaoUnexpectedFormatException] si la estructura HTML cambió.
     */
    fun parseLoginForm(html: String, baseUrl: String): LoginFormResult {
        if (html.isBlank()) {
            throw UpaoUnexpectedFormatException("Respuesta HTML del portal vacía")
        }
        val doc = Jsoup.parse(html, baseUrl)
        val form = doc.selectFirst("form")
            ?: throw UpaoUnexpectedFormatException("No se encontró el formulario <form> de autenticación en la página")

        val inputs = form.select("input")
        if (inputs.isEmpty()) {
            throw UpaoUnexpectedFormatException("El formulario de autenticación no contiene etiquetas <input>")
        }

        val actionAttr = form.attr("action")
        val resolvedAction = if (actionAttr.isNotBlank()) {
            form.absUrl("action").ifBlank { baseUrl }
        } else {
            baseUrl
        }

        val hidden = mutableMapOf<String, String>()
        for (input in inputs) {
            val name = input.attr("name")
            val value = input.attr("value")
            if (name.isNotBlank()) {
                hidden[name] = value
            }
        }

        return LoginFormResult(actionUrl = resolvedAction, hiddenInputs = hidden)
    }

    /**
     * Parsea la respuesta JSON de cursos y notas (studentGrades).
     * Lanza [UpaoUnexpectedFormatException] si el JSON no tiene el arreglo de cursos esperado.
     */
    fun parseCoursesJson(jsonStr: String): List<CourseGrade> {
        if (jsonStr.isBlank()) {
            throw UpaoUnexpectedFormatException("Respuesta JSON de notas vacía")
        }
        val parsed = try {
            JsonParser.parseString(jsonStr)
        } catch (e: Exception) {
            throw UpaoUnexpectedFormatException("Error de sintaxis en JSON de notas", cause = e)
        }

        val array = when {
            parsed.isJsonArray -> parsed.asJsonArray
            parsed.isJsonObject && parsed.asJsonObject.has("data") && parsed.asJsonObject.get("data").isJsonArray ->
                parsed.asJsonObject.getAsJsonArray("data")
            parsed.isJsonObject && parsed.asJsonObject.has("items") && parsed.asJsonObject.get("items").isJsonArray ->
                parsed.asJsonObject.getAsJsonArray("items")
            else -> throw UpaoUnexpectedFormatException("El JSON de notas no contiene la lista de cursos esperada ('data' o raíz)")
        }

        val courses = mutableListOf<CourseGrade>()
        for (elem in array) {
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
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

                courses.add(
                    CourseGrade(
                        nombre = title,
                        courseTitle = title,
                        subjectDescription = obj.get("subjectDescription")?.asStringOrNull(),
                        crn = crn,
                        courseReferenceNumber = crn,
                        rawBanner = rawMap
                    )
                )
            }
        }
        return courses
    }

    /**
     * Parsea la respuesta JSON de horario (registrationHistory o getRegistrationEvents).
     * Lanza [UpaoUnexpectedFormatException] si no se encuentra la estructura de registros esperada.
     */
    fun parseHorarioJson(jsonStr: String): List<JsonObject> {
        if (jsonStr.isBlank()) {
            throw UpaoUnexpectedFormatException("Respuesta JSON de horario vacía")
        }
        val parsed = try {
            JsonParser.parseString(jsonStr)
        } catch (e: Exception) {
            throw UpaoUnexpectedFormatException("Error de sintaxis en JSON de horario", cause = e)
        }

        val registros = mutableListOf<JsonObject>()
        if (parsed.isJsonObject) {
            val root = parsed.asJsonObject
            val dataElem = root.get("data")
            val regArr = when {
                dataElem != null && dataElem.isJsonObject -> {
                    val obj = dataElem.asJsonObject
                    if (obj.has("registrations") && obj.get("registrations").isJsonArray) {
                        obj.getAsJsonArray("registrations")
                    } else {
                        null
                    }
                }
                dataElem != null && dataElem.isJsonArray -> dataElem.asJsonArray
                root.has("registrations") && root.get("registrations").isJsonArray ->
                    root.getAsJsonArray("registrations")
                else -> null
            }

            if (regArr == null && !(root.has("events") && root.get("events").isJsonArray)) {
                throw UpaoUnexpectedFormatException("El JSON de horario no contiene el campo 'registrations' ni 'data'")
            }

            regArr?.forEach { elem ->
                if (elem.isJsonObject) registros.add(elem.asJsonObject)
            }
        } else if (parsed.isJsonArray) {
            parsed.asJsonArray.forEach { elem ->
                if (elem.isJsonObject) registros.add(elem.asJsonObject)
            }
        } else {
            throw UpaoUnexpectedFormatException("La estructura raíz del horario no es un objeto ni un arreglo JSON")
        }

        return registros
    }

    /**
     * Parsea la respuesta JSON paginada de asistencia (studentAttendanceTracking/getRegisteredSections).
     * Lanza [UpaoUnexpectedFormatException] si falta 'data' o 'totalCount'.
     */
    fun parseAsistenciaJson(jsonStr: String): Pair<Int?, List<JsonObject>> {
        if (jsonStr.isBlank()) {
            throw UpaoUnexpectedFormatException("Respuesta JSON de asistencias vacía")
        }
        val parsed = try {
            JsonParser.parseString(jsonStr)
        } catch (e: Exception) {
            throw UpaoUnexpectedFormatException("Error de sintaxis en JSON de asistencias", cause = e)
        }

        if (!parsed.isJsonObject) {
            throw UpaoUnexpectedFormatException("La respuesta de asistencia debe ser un objeto JSON")
        }

        val rootObj = parsed.asJsonObject
        if (!rootObj.has("data") && !rootObj.has("totalCount")) {
            throw UpaoUnexpectedFormatException("El JSON de asistencias no contiene los campos requeridos ('data' o 'totalCount')")
        }

        val totalCount = rootObj.get("totalCount")?.asIntOrNull()
        val dataArr = rootObj.getAsJsonArray("data")
        val items = mutableListOf<JsonObject>()
        if (dataArr != null) {
            for (elem in dataArr) {
                if (elem.isJsonObject) items.add(elem.asJsonObject)
            }
        }

        return totalCount to items
    }

    private fun com.google.gson.JsonElement.asStringOrNull(): String? =
        if (isJsonNull) null else runCatching { asString }.getOrNull()

    private fun com.google.gson.JsonElement.asIntOrNull(): Int? =
        if (isJsonNull) null else runCatching { asInt }.getOrNull()
}
