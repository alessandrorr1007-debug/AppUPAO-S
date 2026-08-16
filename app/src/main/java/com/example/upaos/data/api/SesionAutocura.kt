package com.example.upaos.data.api

import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.LoginRequest
import retrofit2.Response

/** Resultado de una llamada con renovación automática de sesión. */
data class ResultadoLlamada<T>(
    val response: Response<T>,
    val errorBody: String?,
    val tokenRenovado: String?
)

/**
 * Ejecuta una petición autenticada. Si el backend responde 401 con
 * "sesion_expirada" (sesión de Banner caída o token perdido por un reinicio
 * del servidor), intenta un re-login silencioso con las credenciales
 * guardadas y reintenta la petición una vez con el token nuevo. Si el
 * re-login falla, devuelve la respuesta 401 original para que la UI muestre
 * el flujo manual de "Sesión expirada".
 */
suspend fun <T> llamarConRenovacion(
    tokenManager: TokenManager,
    token: String,
    request: suspend (String) -> Response<T>
): ResultadoLlamada<T> {
    val res1 = request(token)
    val err1 = res1.errorBody()?.string()
    if (!esErrorSesionExpirada(res1.code(), err1)) {
        return ResultadoLlamada(res1, err1, null)
    }

    val usuario = tokenManager.getSavedUser()
    val password = tokenManager.getSavedPass()
    if (usuario == null || password == null) {
        return ResultadoLlamada(res1, err1, null)
    }

    return try {
        val loginRes = RetrofitClient.apiService.login(LoginRequest(usuario, password))
        val body = loginRes.body()
        val nuevoToken =
            if (loginRes.isSuccessful && body != null && body.success) body.token else null
        if (nuevoToken.isNullOrEmpty()) {
            ResultadoLlamada(res1, err1, null)
        } else {
            tokenManager.saveToken(nuevoToken)
            tokenManager.saveUserId(usuario)
            val res2 = request(nuevoToken)
            ResultadoLlamada(res2, res2.errorBody()?.string(), nuevoToken)
        }
    } catch (e: Exception) {
        ResultadoLlamada(res1, err1, null)
    }
}
