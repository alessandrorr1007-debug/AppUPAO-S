package com.example.upaos.ui.components

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parsea un timestamp ISO-8601 a milisegundos desde Epoch.
 * Si contiene zona horaria explícita ('Z' o offset ±HH:mm), se respeta la zona.
 * Si es un timestamp ingenuo/local (sin indicador de zona), se interpreta en la zona
 * horaria local del dispositivo para evitar desfases de 5 horas (ej. UTC-5 en Perú).
 */
private fun parsearInstant(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val clean = iso.trim()

    // Si tiene zona horaria explícita ('Z' o ±HH:mm / ±HHmm)
    if (Regex("(Z|[+-]\\d{2}:?\\d{2})$").containsMatchIn(clean)) {
        return com.example.upaos.data.agenda.parseServerDate(clean)
    }

    // Timestamps locales generados en el dispositivo sin zona horaria
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss")) {
        try {
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }
            val pos = java.text.ParsePosition(0)
            val parsed = format.parse(clean, pos)
            if (parsed != null && pos.index == clean.length) {
                return parsed.time
            }
        } catch (_: Exception) {}
    }

    return com.example.upaos.data.agenda.parseServerDate(clean)
}

/** "hace 5 min", "hace un momento", "hace 3 h", "hace 2 días", o "" si no hay fecha. */
fun tiempoRelativo(iso: String?): String {
    val epoch = parsearInstant(iso) ?: return ""
    val diffMillis = System.currentTimeMillis() - epoch
    val minutos = diffMillis / 60000
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

