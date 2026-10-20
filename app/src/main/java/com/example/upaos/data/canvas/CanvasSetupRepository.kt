package com.example.upaos.data.canvas

import com.example.upaos.data.api.CanvasApiService
import com.example.upaos.data.model.CanvasUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface CanvasSetupRepository { suspend fun validateAndSave(token: String): CanvasUser }

class DefaultCanvasSetupRepository(
    private val api: CanvasApiService,
    private val validateOwner: () -> Boolean,
    private val save: (String) -> Unit
) : CanvasSetupRepository {
    override suspend fun validateAndSave(token: String): CanvasUser = withContext(Dispatchers.IO) {
        if (!validateOwner()) throw CancellationException("La cuenta cambió")
        val response = api.getUserSelf("Bearer $token")
        if (response.code() == 401) {
            response.errorBody()?.close()
            throw java.io.IOException("Token inválido o expirado. Verifica que lo hayas copiado completo.")
        }
        val user = response.completeBody()
        if (!validateOwner()) throw CancellationException("La cuenta cambió")
        save(token)
        user
    }
}
