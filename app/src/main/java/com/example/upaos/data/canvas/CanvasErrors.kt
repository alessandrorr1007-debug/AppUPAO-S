package com.example.upaos.data.canvas

import java.io.IOException
import retrofit2.HttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

enum class CanvasError(val text: String) {
    TOKEN("Token expirado"), FORBIDDEN("Sin permiso o función deshabilitada en este curso"),
    MISSING("No disponible"), BUSY("Canvas está lento, intenta luego"),
    NETWORK("Sin conexión con Canvas"), INVALID("Respuesta de Canvas no válida")
}
fun canvasError(error: Throwable): CanvasError {
    if (error is HttpException) return when (error.code()) {
        401 -> CanvasError.TOKEN
        403 -> CanvasError.FORBIDDEN
        404 -> CanvasError.MISSING
        429, in 500..599 -> CanvasError.BUSY
        else -> CanvasError.INVALID
    }
    return error.cause?.takeIf { it !== error }?.let(::canvasError)
        ?: if (error is IOException) CanvasError.NETWORK else CanvasError.INVALID
}
suspend fun <T> canvasReadWithBackoff(block: suspend () -> T): T {
    repeat(3) { attempt ->
        try { return block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (canvasError(e) != CanvasError.BUSY || attempt == 2) throw e
            delay(500L shl attempt)
        }
    }
    error("unreachable")
}
