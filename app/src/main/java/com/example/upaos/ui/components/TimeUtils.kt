package com.example.upaos.ui.components

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * El backend guarda los timestamps con datetime.now() sin zona horaria (naive).
 * En producción (Render) el reloj del servidor está en UTC, así que se interpretan
 * como UTC y se comparan con el reloj del dispositivo.
 */
private fun parsearInstant(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        val base = if (iso.contains(".")) iso.substringBefore(".") else iso
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(base)?.time
    } catch (e: Exception) {
        null
    }
}

/** "hace 5 min", "hace un momento", "hace 3 h", "hace 2 días", o "" si no hay fecha. */
fun tiempoRelativo(iso: String?): String {
    val epoch = parsearInstant(iso) ?: return ""
    val minutos = (System.currentTimeMillis() - epoch) / 60000
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace $minutos min"
        minutos < 60 * 24 -> "hace ${minutos / 60} h"
        minutos < 60 * 24 * 7 -> "hace ${minutos / (60 * 24)} días"
        else -> "el ${fechaLocal(epoch)}"
    }
}

/** "14:32" en la zona horaria del dispositivo. */
fun horaLocal(iso: String?): String {
    val epoch = parsearInstant(iso) ?: return ""
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    fmt.timeZone = TimeZone.getDefault()
    return fmt.format(epoch)
}

/** "02 ago" en la zona horaria del dispositivo. */
private fun fechaLocal(epoch: Long): String {
    val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    fmt.timeZone = TimeZone.getDefault()
    return fmt.format(epoch)
}

/** Texto "Actualizado hace X" / "Actualizado a las HH:mm". */
fun textoUltimaActualizacion(iso: String?): String {
    val epoch = parsearInstant(iso) ?: return ""
    val minutos = (System.currentTimeMillis() - epoch) / 60000
    return when {
        minutos < 1 -> "Actualizado hace un momento"
        minutos < 60 -> "Actualizado hace $minutos min"
        else -> "Actualizado a las ${horaLocal(iso)}"
    }
}
