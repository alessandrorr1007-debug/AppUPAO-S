package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("password") val password: String
)

data class ManualCaptchaRequest(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("password") val password: String,
    @SerializedName("codigo_manual") val codigoManual: String
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String?,
    @SerializedName("necesita_captcha") val necesitaCaptcha: Boolean,
    @SerializedName("imagen_base64") val imagenBase64: String?,
    @SerializedName("es_admin") val esAdmin: Boolean = false,
    @SerializedName("message") val message: String?
)
