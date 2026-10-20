package com.example.upaos.data.upao

import okio.buffer
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

    class AccountSession internal constructor(val user: String) {
        val cookieJar = UpaoCookieJar(user)
        val loginMutex = kotlinx.coroutines.sync.Mutex()
        @Volatile var authenticatedUser: String? = null
            internal set
        @Volatile private var valid = true
        fun requireValid() { if (!valid) throw kotlinx.coroutines.CancellationException("La cuenta cambió") }
        internal fun invalidate() { valid = false; authenticatedUser = null; cookieJar.invalidate(); client.dispatcher.cancelAll() }
        val client: OkHttpClient = buildClient(cookieJar).newBuilder().addInterceptor { chain ->
            requireValid()
            val response = chain.proceed(chain.request())
            try {
                requireValid()
                val body = response.body
                if (body == null) response else response.newBuilder().body(object : okhttp3.ResponseBody() {
                    private val guardedSource = object : okio.ForwardingSource(body.source()) {
                        override fun read(sink: okio.Buffer, byteCount: Long): Long {
                            requireValid()
                            val read = super.read(sink, byteCount)
                            requireValid()
                            return read
                        }
                    }.buffer()
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun source(): okio.BufferedSource = guardedSource
                }).build()
            } catch (e: kotlinx.coroutines.CancellationException) { response.close(); throw e }
        }.build()
    }

    private val sessions = mutableMapOf<String, AccountSession>()
    private var selectedUser: String? = null

    /** Only login/explicit account selection may change the selected identity. */
    @Synchronized fun selectAccount(user: String): AccountSession {
        val normalized = user.trim()
        require(normalized.isNotEmpty())
        if (selectedUser != normalized) {
            sessions.values.forEach { it.invalidate() }
            sessions.clear()
            selectedUser = normalized
        }
        return sessions.getOrPut(normalized) { AccountSession(normalized) }
    }

    /** Restore only from a persisted login; old repositories cannot resurrect a logged-out account. */
    @Synchronized fun restoreAccount(user: String) {
        if (selectedUser == null) selectAccount(user)
    }

    @Synchronized fun forAccount(user: String): AccountSession {
        val normalized = user.trim()
        if (selectedUser == null) throw kotlinx.coroutines.CancellationException("No hay sesión activa")
        if (selectedUser != normalized) throw kotlinx.coroutines.CancellationException("La cuenta cambió")
        return sessions.getOrPut(normalized) { AccountSession(normalized) }
    }

    @Synchronized fun clearAccount(user: String) {
        sessions.remove(user.trim())?.invalidate()
        if (selectedUser == user.trim()) selectedUser = null
    }

    private val defaultHeadersInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", USER_AGENT)
        }

        chain.proceed(builder.build())
    }

    private fun buildClient(cookieJar: UpaoCookieJar): OkHttpClient = OkHttpClient.Builder()
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
