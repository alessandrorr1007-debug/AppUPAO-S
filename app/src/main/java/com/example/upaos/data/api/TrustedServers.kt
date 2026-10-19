package com.example.upaos.data.api

import okhttp3.HttpUrl

object TrustedServers {
    fun isLogin(url: HttpUrl): Boolean = url.isHttps && url.username.isEmpty() &&
        url.password.isEmpty() && url.host in setOf("upaosso.upao.edu.pe", "ssb.upao.edu.pe")

    fun isCanvas(url: HttpUrl): Boolean = url.isHttps && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() && url.host == "upao.instructure.com"
}
