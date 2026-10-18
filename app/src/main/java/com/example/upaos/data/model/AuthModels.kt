package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String?,
    @SerializedName("necesita_captcha") val necesitaCaptcha: Boolean = false,
    @SerializedName("imagen_base64") val imagenBase64: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("captcha_id") val captchaId: String? = null
)
