package com.example.upaos.data.api

/**
 * True cuando el backend respondió HTTP 401 con detail="sesion_expirada":
 * señal clara de que la sesión de Banner caducó y el usuario debe volver a
 * iniciar sesión (distinta de un error genérico de red o servidor).
 */
fun esErrorSesionExpirada(codigo: Int, cuerpoError: String?): Boolean =
    codigo == 401 && cuerpoError?.contains("sesion_expirada") == true
