package com.example.upaos.data.api

import android.content.Context
import android.util.Log
import com.example.upaos.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class AuthInterceptor(private val contextProvider: () -> Context?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // Si la respuesta es HTTP 401 y no proviene de la propia ruta /login
        if (response.code == 401 && !originalRequest.url.encodedPath.contains("login")) {
            Log.d("UPAO_APP", "[AuthInterceptor] HTTP 401 recibido en ${originalRequest.url.encodedPath}. Intentando re-login en segundo plano...")

            val context = contextProvider()
            if (context != null) {
                val tokenManager = TokenManager(context)
                val user = tokenManager.getSavedUser()
                val pass = tokenManager.getSavedPass()

                if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                    synchronized(this) {
                        val currentToken = tokenManager.getToken()
                        val requestAuthHeader = originalRequest.header("Authorization")
                        val requestToken = requestAuthHeader?.replace("Bearer ", "")?.trim()

                        // Si otra petición renovó el token mientras esperábamos en synchronized
                        if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
                            Log.d("UPAO_APP", "[AuthInterceptor] Token ya fue actualizado por otro hilo. Reintentando petición...")
                            response.close()
                            val newRequest = originalRequest.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                            return chain.proceed(newRequest)
                        }

                        val newToken = intentarReLoginSincrono(chain, user, pass, tokenManager)
                        if (!newToken.isNullOrBlank()) {
                            Log.d("UPAO_APP", "[AuthInterceptor] Re-login automático exitoso. Reintentando petición original con nuevo token...")
                            response.close()
                            val newRequest = originalRequest.newBuilder()
                                .header("Authorization", "Bearer $newToken")
                                .build()
                            return chain.proceed(newRequest)
                        }
                    }
                } else {
                    Log.w("UPAO_APP", "[AuthInterceptor] No se encontraron credenciales guardadas para auto-login.")
                }
            }
        }

        return response
    }

    private fun intentarReLoginSincrono(
        chain: Interceptor.Chain,
        user: String,
        pass: String,
        tokenManager: TokenManager
    ): String? {
        return try {
            val loginUrl = chain.request().url.newBuilder().encodedPath("/login").build()
            val jsonBody = JSONObject().apply {
                put("usuario", user)
                put("password", pass)
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val loginRequest = Request.Builder()
                .url(loginUrl)
                .post(jsonBody.toRequestBody(mediaType))
                .build()

            val loginClient = OkHttpClient.Builder().build()
            val loginResponse = loginClient.newCall(loginRequest).execute()

            if (loginResponse.isSuccessful) {
                val responseBodyStr = loginResponse.body?.string()
                if (!responseBodyStr.isNullOrBlank()) {
                    val jsonObj = JSONObject(responseBodyStr)
                    val success = jsonObj.optBoolean("success", false)
                    val token = jsonObj.optString("token", null)

                    if (success && !token.isNullOrBlank()) {
                        tokenManager.saveToken(token)
                        tokenManager.setKeepLoggedIn(true)
                        return token
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("UPAO_APP", "[AuthInterceptor] Error en re-login automático: ${e.localizedMessage}")
            null
        }
    }
}
