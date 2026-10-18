package com.example.upaos.data.upao

import java.io.IOException

sealed class UpaoException(message: String, cause: Throwable? = null) : IOException(message, cause)

class UpaoInvalidCredentialsException(
    message: String = "ID o contraseña incorrectos. Revisa tus datos."
) : UpaoException(message)

class UpaoServiceUnavailableException(
    message: String = "El portal de la UPAO no está disponible, intenta más tarde."
) : UpaoException(message)

class UpaoSessionExpiredException(
    message: String = "Tu sesión en la UPAO ha expirado. Por favor, inicia sesión nuevamente."
) : UpaoException(message)

class UpaoNetworkException(
    message: String = "Error de conexión con la UPAO. Verifica tu conexión a internet.",
    cause: Throwable? = null
) : UpaoException(message, cause)

class UpaoUnexpectedFormatException(
    val detalle: String = "Estructura no esperada en la respuesta",
    message: String = "La UPAO cambió algo en su portal y la app necesita actualizarse. Mostrando lo último guardado.",
    cause: Throwable? = null
) : UpaoException("$message ($detalle)", cause)

// Aliases requeridos por la especificación
typealias FormatoInesperado = UpaoUnexpectedFormatException
typealias SesionExpirada = UpaoSessionExpiredException
typealias SinConexion = UpaoNetworkException
typealias UpaoNoDisponible = UpaoServiceUnavailableException
typealias CredencialesInvalidas = UpaoInvalidCredentialsException
