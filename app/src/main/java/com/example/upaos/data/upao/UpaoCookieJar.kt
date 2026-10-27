package com.example.upaos.data.upao

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.JavaNetCookieJar
import java.net.CookieManager
import java.net.CookiePolicy

/** A jar belongs to one immutable account, including responses arriving after a switch. */
class UpaoCookieJar(val account: String) : CookieJar {
    private val manager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val delegate = JavaNetCookieJar(manager)
    @Volatile private var valid = true
    @Synchronized fun invalidate() { valid = false; manager.cookieStore.removeAll() }
    @Synchronized fun clear() { manager.cookieStore.removeAll() }
    @Synchronized fun hasCookieForDomain(domain: String): Boolean = valid && manager.cookieStore.cookies.any {
        !it.hasExpired() && it.domain?.trimStart('.')?.equals(domain, ignoreCase = true) == true
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (valid) delegate.saveFromResponse(url, cookies)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (valid) delegate.loadForRequest(url) else emptyList()
}
