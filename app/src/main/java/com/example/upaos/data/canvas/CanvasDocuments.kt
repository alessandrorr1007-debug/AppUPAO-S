package com.example.upaos.data.canvas

import com.example.upaos.data.model.CanvasFileDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Signed file URLs receive no bearer token or cookies, including across CDN redirects. */
object CanvasDocuments {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    const val MAX_BYTES = 32L * 1024 * 1024
    fun safeUrl(raw: String): HttpUrl? = raw.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
            (it.host == "upao.instructure.com" || it.host.endsWith(".instructure.com") ||
                it.host.endsWith(".canvas-user-content.com") || it.host.endsWith(".amazonaws.com"))
    }
    suspend fun download(metadata: CanvasFileDetail, directory: File, validate: () -> Unit): File = withContext(Dispatchers.IO) {
        validate()
        check(metadata.lockedForUser != true && metadata.hiddenForUser != true && metadata.locked != true) { "Archivo restringido" }
        require((metadata.size ?: 0) <= MAX_BYTES) { "El archivo supera 32 MB" }
        var url = safeUrl(metadata.url.orEmpty()) ?: throw IOException("Destino de archivo no autorizado")
        directory.mkdirs()
        val extension = metadata.fileName.substringAfterLast('.', "bin").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
        val target = File.createTempFile("canvas-${metadata.id}-", ".$extension", directory)
        try {
            repeat(6) {
                validate()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code in 300..399) {
                        val redirect = response.header("Location")?.let { url.resolve(it) } ?: throw IOException("Redirección inválida")
                        url = safeUrl(redirect.toString()) ?: throw IOException("Redirección de archivo no autorizada")
                    } else {
                        check(response.isSuccessful) { "No se pudo descargar el archivo" }
                        val body = checkNotNull(response.body)
                        check(body.contentLength() <= MAX_BYTES)
                        body.byteStream().use { input -> target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                validate()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= MAX_BYTES)
                                output.write(buffer, 0, count)
                            }
                        } }
                        validate()
                        return@withContext target
                    }
                }
            }
            throw IOException("Demasiadas redirecciones")
        } catch (e: Exception) { target.delete(); throw e }
    }
}

fun canvasDocumentOwner(user: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(user.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
