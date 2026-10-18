package com.example.upaos.data.upao

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.JavaNetCookieJar
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.ConcurrentHashMap

/**
 * CookieJar en memoria con aislamiento por cuenta de usuario.
 * Utiliza JavaNetCookieJar de okhttp-urlconnection.
 */
class UpaoCookieJar : CookieJar {

    private val managersByAccount = ConcurrentHashMap<String, CookieManager>()
    private val delegateJarsByAccount = ConcurrentHashMap<String, JavaNetCookieJar>()

    @Volatile
    private var activeAccount: String = "default"

    private fun getOrCreateManager(account: String): CookieManager {
        return managersByAccount.computeIfAbsent(account) {
            CookieManager(null, CookiePolicy.ACCEPT_ALL)
        }
    }

    private fun getDelegateJar(account: String): JavaNetCookieJar {
        return delegateJarsByAccount.computeIfAbsent(account) {
            JavaNetCookieJar(getOrCreateManager(it))
        }
    }

    fun switchAccount(account: String) {
        activeAccount = account.trim().ifEmpty { "default" }
    }

    fun clearActiveAccount() {
        managersByAccount[activeAccount]?.cookieStore?.removeAll()
    }

    fun clearAccount(account: String) {
        val acc = account.trim().ifEmpty { "default" }
        managersByAccount[acc]?.cookieStore?.removeAll()
        managersByAccount.remove(acc)
        delegateJarsByAccount.remove(acc)
    }

    fun clearAll() {
        managersByAccount.values.forEach { it.cookieStore.removeAll() }
        managersByAccount.clear()
        delegateJarsByAccount.clear()
    }

    fun hasCookieForDomain(domainFragment: String): Boolean {
        val manager = managersByAccount[activeAccount] ?: return false
        return manager.cookieStore.cookies.any { cookie ->
            val cookieDomain = cookie.domain
            cookieDomain != null && cookieDomain.contains(domainFragment, ignoreCase = true)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        getDelegateJar(activeAccount).saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return getDelegateJar(activeAccount).loadForRequest(url)
    }
}
