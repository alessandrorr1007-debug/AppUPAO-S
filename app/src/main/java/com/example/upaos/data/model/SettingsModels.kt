package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class DeviceTokenRequest(
    @SerializedName("fcm_token") val fcmToken: String
)

data class IntervaloRequest(
    @SerializedName("minutos") val minutos: Int
)

data class SettingsResponse(
    @SerializedName("auto_check_enabled") val autoCheckEnabled: Boolean,
    @SerializedName("intervalo_chequeo_minutos") val intervaloMinutos: Int,
    @SerializedName("tiene_token_fcm") val tieneTokenFcm: Boolean = false,
    @SerializedName("ultima_revision") val ultimaRevision: String? = null
)
