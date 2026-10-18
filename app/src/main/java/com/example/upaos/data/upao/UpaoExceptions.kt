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
