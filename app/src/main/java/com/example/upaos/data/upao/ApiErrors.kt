package com.example.upaos.data.upao

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrors {

    const val MSG_SIN_CONEXION = "Sin conexión. Mostrando lo último guardado."
    const val MSG_UPAO_CAIDO = "El portal de la UPAO no responde ahora. Mostrando lo último guardado."
    const val MSG_FORMATO_INESPERADO = "La UPAO cambió algo en su portal y la app necesita actualizarse. Mostrando lo último guardado."
    const val MSG_CREDENCIALES_INVALIDAS = "ID o contraseña incorrectos. Revisa tus datos e inicia sesión nuevamente."
    const val MSG_SESION_EXPIRADA = "Tu sesión en la UPAO ha expirado. Por favor, inicia sesión nuevamente."

    /**
     * Devuelve el mensaje estandarizado para la interfaz según el tipo de error capturado.
     * Ningún mensaje incluye contraseñas, cookies, tokens ni HTML crudo.
     */
    fun mensajeParaUsuario(throwable: Throwable?): String {
        return when (throwable) {
            is UpaoInvalidCredentialsException -> MSG_CREDENCIALES_INVALIDAS
            is UpaoSessionExpiredException -> MSG_SESION_EXPIRADA
            is UpaoUnexpectedFormatException -> MSG_FORMATO_INESPERADO
            is UpaoServiceUnavailableException -> MSG_UPAO_CAIDO
            is UpaoNetworkException, is UnknownHostException, is SocketTimeoutException -> MSG_SIN_CONEXION
            is IOException -> {
                val msg = throwable.message?.lowercase().orEmpty()
                if (msg.contains("timeout") || msg.contains("connect") || msg.contains("host") || msg.contains("network")) {
                    MSG_SIN_CONEXION
                } else {
                    MSG_UPAO_CAIDO
                }
            }
            else -> MSG_UPAO_CAIDO
        }
    }
}
