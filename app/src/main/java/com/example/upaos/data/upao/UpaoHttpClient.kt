package com.example.upaos.data.upao

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Cliente OkHttp centralizado para la comunicación directa con servidores UPAO.
 * Configurado con followRedirects=true, timeouts de 30s, User-Agent móvil y CookieJar en memoria.
 * No contiene interceptores de logging de cuerpo para proteger credenciales y datos sensibles.
 */
object UpaoHttpClient {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val cookieJar = UpaoCookieJar()

    private val defaultHeadersInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", USER_AGENT)
        }

        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(defaultHeadersInterceptor)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val form = request.body as? okhttp3.FormBody
            if (form != null && (0 until form.size).any { form.name(it) == "nip" } &&
                !com.example.upaos.data.api.TrustedServers.isLogin(request.url)) {
                throw java.io.IOException("Destino de autenticación no autorizado")
            }
            chain.proceed(request)
        }
        .build()
}
