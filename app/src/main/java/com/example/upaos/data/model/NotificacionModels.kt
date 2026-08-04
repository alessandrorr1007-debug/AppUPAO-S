package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class NotificacionItem(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("mensaje") val mensaje: String = "",
    @SerializedName("curso") val curso: String? = null,
    @SerializedName("componente") val componente: String? = null,
    @SerializedName("fecha_creacion") val fechaCreacion: String? = null,
    @SerializedName("leida") val leida: Boolean = false
)

data class NotificacionesResponse(
    @SerializedName("no_leidas") val noLeidas: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("notificaciones") val notificaciones: List<NotificacionItem> = emptyList()
)
