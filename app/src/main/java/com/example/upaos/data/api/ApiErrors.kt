package com.example.upaos.data.api

import com.google.gson.JsonParser
import com.example.upaos.data.model.LoginResponse

private fun credencialesIncorrectas(body: LoginResponse?): Boolean {
    val message = body?.message?.lowercase().orEmpty()
    return body?.errorCode == "invalid_credentials" ||
        listOf("credenciales incorrect", "contraseña incorrect", "id incorrect").any { message.contains(it) }
}

fun mensajeErrorLogin(codigo: Int, body: LoginResponse?, cuerpoError: String? = null): String {
    if (credencialesIncorrectas(body) || codigo == 401) {
        return "ID o contraseña incorrectos. Revisa tus datos."
    }
    if (codigo == 429) return "Demasiados intentos. Espera un momento y vuelve a intentarlo."
    if (codigo == 422) return "El ID debe tener 9 dígitos y la contraseña no puede estar vacía."
    if (codigo >= 500 || body?.errorCode == "service_unavailable") {
        return "No se pudo conectar con UPAO. Inténtalo nuevamente en unos minutos."
    }
    if (!body?.message.isNullOrBlank()) return body!!.message!!
    val detail = runCatching {
        JsonParser.parseString(cuerpoError).asJsonObject.get("detail")?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull()
    return detail ?: "No se pudo iniciar sesión. Revisa tus datos e inténtalo nuevamente."
}

fun requiereCaptcha(body: LoginResponse?): Boolean =
    body?.success == false && body.necesitaCaptcha &&
        !body.imagenBase64.isNullOrBlank() && !credencialesIncorrectas(body)

/**
 * True cuando el backend respondió HTTP 401 con detail="sesion_expirada":
 * señal clara de que la sesión de Banner caducó y el usuario debe volver a
 * iniciar sesión (distinta de un error genérico de red o servidor).
 */
fun esErrorSesionExpirada(codigo: Int, cuerpoError: String?): Boolean =
    codigo == 401 && cuerpoError?.contains("sesion_expirada") == true
