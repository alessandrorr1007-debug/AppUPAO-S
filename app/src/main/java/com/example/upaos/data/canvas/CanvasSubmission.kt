package com.example.upaos.data.canvas

import com.example.upaos.data.api.CanvasApiService
import okhttp3.OkHttpClient
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

interface CanvasSubmissionRepository {
    suspend fun submit(
    token: String,
    courseId: Long,
    assignmentId: Long,
    fileName: String,
    fileSize: Long,
    fileBytes: ByteArray,
    mimeType: String
): Boolean
}

class DefaultCanvasSubmissionRepository(
    private val api: CanvasApiService,
    private val http: OkHttpClient
) : CanvasSubmissionRepository {
override suspend fun submit(
    token: String,
    courseId: Long,
    assignmentId: Long,
    fileName: String,
    fileSize: Long,
    fileBytes: ByteArray,
    mimeType: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val authHeader = "Bearer $token"
        // Paso 1: Notificar a Canvas la intención de subir archivo para entrega
        val initResp = api.initSubmissionFileUpload(
            authHeader = authHeader,
            courseId = courseId,
            assignmentId = assignmentId,
            name = fileName,
            size = fileSize
        )
        if (!initResp.isSuccessful || initResp.body() == null) {
            return@withContext false
        }
        val initData = initResp.body()!!

        // Paso 2: Subir el archivo al upload_url (S3 o Canvas)
        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        for ((key, value) in initData.uploadParams.orEmpty()) {
            multipartBuilder.addFormDataPart(key, value)
        }
        multipartBuilder.addFormDataPart(
            "file",
            fileName,
            fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

        val uploadRequest = Request.Builder()
            .url(initData.uploadUrl)
            .post(multipartBuilder.build())
            .build()

        val uploadResponseBody = http.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            response.body?.string() ?: return@withContext false
        }

        // Parsear el ID del archivo devuelto por Canvas
        val jsonElement = JsonParser.parseString(uploadResponseBody)
        if (!jsonElement.isJsonObject) {
            return@withContext false
        }
        val fileId = jsonElement.asJsonObject.get("id")?.asLong ?: return@withContext false

        // Paso 3: Registrar formalmente la entrega con el fileId
        val submitResp = api.submitAssignment(
            authHeader = authHeader,
            courseId = courseId,
            assignmentId = assignmentId,
            fileId = fileId
        )

        submitResp.isSuccessful
    } catch (e: kotlinx.coroutines.CancellationException) { throw e
    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e;
        android.util.Log.w("CanvasSubmission", "No se pudo completar la entrega")
        false
    }
}


}
