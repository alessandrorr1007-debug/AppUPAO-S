package com.example.upaos.service

import com.example.upaos.data.upao.AcademicCalendar
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.min

/**
 * Componente puro y desacoplado para determinar el próximo intervalo de sincronización
 * adaptativa de UPAO S en segundo plano.
 */
object SyncIntervalPolicy {

    val ZONE_LIMA: ZoneId = ZoneId.of("America/Lima")

    const val INTERVALO_CLASE_MINUTOS = 10L
    const val INTERVALO_NOCHE_MINUTOS = 60L
    const val INTERVALO_DOMINGO_MINUTOS = 60L
    const val INTERVALO_FUERA_CICLO_MINUTOS = 60L
    const val INTERVALO_MAX_ERROR_MINUTOS = 60L

    data class SyncContext(
        val fallosConsecutivos: Int = 0,
        val esErrorFatal: Boolean = false,
        val periodoAcademico: String = "202610",
        val esSemanaActivaOverride: Boolean? = null
    )

    /**
     * Calcula la duración recomendada antes de la próxima consulta.
     *
     * @param ahora instante de consulta (se proyecta a zona horaria America/Lima).
     * @param contexto información de fallos consecutivos, errores fatales y calendario académico.
     * @return Duration con el intervalo calculado, o Duration.ZERO si ocurrió un error fatal
     *         para evitar bucles de reintentos innecesarios.
     */
    fun proximoIntervalo(
        ahora: ZonedDateTime,
        contexto: SyncContext = SyncContext()
    ): Duration {
        // 1. Sesión expirada permanentemente o formato inesperado de UPAO -> no insistir en bucle
        if (contexto.esErrorFatal) {
            return Duration.ZERO
        }

        val fechaLima = ahora.withZoneSameInstant(ZONE_LIMA)
        val hora = fechaLima.hour
        val diaSemana = fechaLima.dayOfWeek

        // 2. Si hay fallos consecutivos (red, timeout, 5xx): backoff exponencial (máx 60 min)
        if (contexto.fallosConsecutivos > 0) {
            val factor = 1L shl min(contexto.fallosConsecutivos, 3)
            val minutosBackoff = min(INTERVALO_MAX_ERROR_MINUTOS, INTERVALO_CLASE_MINUTOS * factor)
            return Duration.ofMinutes(minutosBackoff)
        }

        // 3. Noche (22:00 a 07:00)
        if (hora >= 22 || hora < 7) {
            return Duration.ofMinutes(INTERVALO_NOCHE_MINUTOS)
        }

        // 4. Domingo
        if (diaSemana == DayOfWeek.SUNDAY) {
            return Duration.ofMinutes(INTERVALO_DOMINGO_MINUTOS)
        }

        // 5. Fuera de las 16 semanas académicas regulares según AcademicCalendar
        val esSemanaActiva = contexto.esSemanaActivaOverride ?: run {
            val fechaRef = Date.from(fechaLima.toInstant())
            val infoSemana = AcademicCalendar.calcularSemana(contexto.periodoAcademico, fechaRef)
            infoSemana.configurada && infoSemana.fueraDeCiclo == false && infoSemana.semana != null && infoSemana.semana in 1..AcademicCalendar.TOTAL_SEMANAS_REGULARES
        }

        if (!esSemanaActiva) {
            return Duration.ofMinutes(INTERVALO_FUERA_CICLO_MINUTOS)
        }

        // 6. Horario diurno regular de clases (07:00 a 22:00, Lunes a Sábado en semana académica activa)
        return Duration.ofMinutes(INTERVALO_CLASE_MINUTOS)
    }
}
