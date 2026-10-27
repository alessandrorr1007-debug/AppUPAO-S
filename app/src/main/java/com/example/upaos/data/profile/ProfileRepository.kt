package com.example.upaos.data.profile

import android.content.Context
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.upao.UpaoHttpClient
import com.example.upaos.data.upao.UpaoAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface ProfileResult {
    data class Updated(val profile: StudentProfile) : ProfileResult
    data object AuthenticationRequired : ProfileResult
    data object Unavailable : ProfileResult
}

/** Reuse account cookies; establish the Experience SAML session with the existing UPAO credentials when needed. */
class ProfileRepository(context: Context, private val user: String, private val sessionId: String) {
    private val tokens = TokenManager(context.applicationContext)
    fun isCurrent(): Boolean = tokens.getSavedUser() == user && tokens.getSessionId() == sessionId
    suspend fun cached(): StudentProfile? = withContext(Dispatchers.IO) {
        if (!isCurrent()) return@withContext null
        try { tokens.getProfileJson(user)?.let(StudentProfile::parse) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    suspend fun refresh(): ProfileResult = withContext(Dispatchers.IO) {
        if (!isCurrent()) return@withContext ProfileResult.Unavailable
        try {
            val account = UpaoHttpClient.forAccount(user)
            val client = account.client.newBuilder()
                .followRedirects(false).followSslRedirects(false)
                .callTimeout(30, TimeUnit.SECONDS).build()
            var result = readProfile(client)
            if (result == ProfileResult.AuthenticationRequired && isCurrent()) {
                // Finish an existing saved Banner login first, so its bootstrap cannot clear freshly acquired Experience cookies.
                if (account.authenticatedUser != user) {
                    val pass = tokens.getSavedPass() ?: return@withContext ProfileResult.AuthenticationRequired
                    if (!isCurrent()) return@withContext ProfileResult.Unavailable
                    if (!UpaoAuth.ensureSession(user, pass).success) return@withContext ProfileResult.AuthenticationRequired
                }
                val authenticated = account.loginMutex.withLock {
                    account.requireValid()
                    ExperienceAuth.authenticate(client, user, tokens::getSavedPass, ::isCurrent)
                }
                if (authenticated && isCurrent()) result = readProfile(client)
            }
            result
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { ProfileResult.Unavailable }
    }
    private suspend fun readProfile(client: okhttp3.OkHttpClient): ProfileResult {
        if (!isCurrent()) return ProfileResult.Unavailable
        val request = Request.Builder().url(ENDPOINT).header("Accept", "application/json").get().build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403 || response.code in 300..399)
                return@use ProfileResult.AuthenticationRequired
            if (!response.isSuccessful) return@use ProfileResult.Unavailable
            val type = response.body?.contentType()
            if (type?.subtype?.contains("json", ignoreCase = true) != true) return@use ProfileResult.Unavailable
            val json = response.body?.string() ?: return@use ProfileResult.Unavailable
            val profile = StudentProfile.parse(json)
            currentCoroutineContext().ensureActive()
            if (!tokens.saveProfileIfCurrent(user, sessionId, json, profile.fullName, profile.shortName)) return@use ProfileResult.Unavailable
            ProfileResult.Updated(profile)
        }
    }
    companion object { const val ENDPOINT = "https://experience.elluciancloud.com/upao/api/profile" }
}
