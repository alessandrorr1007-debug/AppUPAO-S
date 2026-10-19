package com.example.upaos

import com.example.upaos.service.SyncIntervalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class SyncIntervalPolicyTest {

    private val limaZone = ZoneId.of("America/Lima")

    @Test
    fun lunesEnHorarioDeClases_retorna10Minutos() {
        // Lunes 2026-04-13 a las 10:00 (dentro del ciclo regular 202610)
        val lunes10am = ZonedDateTime.of(2026, 4, 13, 10, 0, 0, 0, limaZone)
        val contexto = SyncIntervalPolicy.SyncContext(
            fallosConsecutivos = 0,
            esSemanaActivaOverride = true
        )

        val intervalo = SyncIntervalPolicy.proximoIntervalo(lunes10am, contexto)
        assertEquals(Duration.ofMinutes(10), intervalo)
    }

    @Test
    fun noche_retorna60Minutos() {
        // Martes 2026-04-14 a las 23:30
        val martesNoche = ZonedDateTime.of(2026, 4, 14, 23, 30, 0, 0, limaZone)
        val contexto = SyncIntervalPolicy.SyncContext(
            fallosConsecutivos = 0,
            esSemanaActivaOverride = true
        )

        val intervalo = SyncIntervalPolicy.proximoIntervalo(martesNoche, contexto)
        assertEquals(Duration.ofMinutes(60), intervalo)
    }

    @Test
    fun madrugada_retorna60Minutos() {
        // Miércoles 2026-04-15 a las 04:15
        val miercolesMadrugada = ZonedDateTime.of(2026, 4, 15, 4, 15, 0, 0, limaZone)
        val contexto = SyncIntervalPolicy.SyncContext(
            fallosConsecutivos = 0,
            esSemanaActivaOverride = true
        )

        val intervalo = SyncIntervalPolicy.proximoIntervalo(miercolesMadrugada, contexto)
        assertEquals(Duration.ofMinutes(60), intervalo)
    }

    @Test
    fun domingo_retorna60Minutos() {
        // Domingo 2026-04-19 a las 15:00
        val domingo15pm = ZonedDateTime.of(2026, 4, 19, 15, 0, 0, 0, limaZone)
        val contexto = SyncIntervalPolicy.SyncContext(
            fallosConsecutivos = 0,
            esSemanaActivaOverride = true
        )

        val intervalo = SyncIntervalPolicy.proximoIntervalo(domingo15pm, contexto)
        assertEquals(Duration.ofMinutes(60), intervalo)
    }

    @Test
    fun fueraDeCicloAcademico_retorna60Minutos() {
        // Lunes 10:00 pero ciclo no ha iniciado o ya culminó
        val lunes10am = ZonedDateTime.of(2026, 4, 13, 10, 0, 0, 0, limaZone)
        val contexto = SyncIntervalPolicy.SyncContext(
            fallosConsecutivos = 0,
            esSemanaActivaOverride = false
        )

        val intervalo = SyncIntervalPolicy.proximoIntervalo(lunes10am, contexto)
        assertEquals(Duration.ofMinutes(60), intervalo)
    }

    @Test
    fun fallosConsecutivos_aplicanBackoffExponencialHastaTopeDe60Minutos() {
        val lunes10am = ZonedDateTime.of(2026, 4, 13, 10, 0, 0, 0, limaZone)

        // 1 fallo -> 20 min
        val ctx1 = SyncIntervalPolicy.SyncContext(fallosConsecutivos = 1, esSemanaActivaOverride = true)
        assertEquals(Duration.ofMinutes(20), SyncIntervalPolicy.proximoIntervalo(lunes10am, ctx1))

        // 2 fallos -> 40 min
        val ctx2 = SyncIntervalPolicy.SyncContext(fallosConsecutivos = 2, esSemanaActivaOverride = true)
        assertEquals(Duration.ofMinutes(40), SyncIntervalPolicy.proximoIntervalo(lunes10am, ctx2))

        // 3 fallos -> 60 min (tope)
        val ctx3 = SyncIntervalPolicy.SyncContext(fallosConsecutivos = 3, esSemanaActivaOverride = true)
        assertEquals(Duration.ofMinutes(60), SyncIntervalPolicy.proximoIntervalo(lunes10am, ctx3))

        // 5 fallos -> 60 min (tope no excede 60)
        val ctx5 = SyncIntervalPolicy.SyncContext(fallosConsecutivos = 5, esSemanaActivaOverride = true)
        assertEquals(Duration.ofMinutes(60), SyncIntervalPolicy.proximoIntervalo(lunes10am, ctx5))
    }

    @Test
    fun vueltaANormalTrasExito() {
        val lunes10am = ZonedDateTime.of(2026, 4, 13, 10, 0, 0, 0, limaZone)

        // Supongamos que veníamos de 3 fallos (60 min)
        val ctxPrevio = SyncIntervalPolicy.SyncContext(fallosConsecutivos = 3, esSemanaActivaOverride = true)
        assertEquals(Duration.ofMinutes(60), SyncIntervalPolicy.proximoIntervalo(lunes10am, ctxPrevio))

        // Éxito resetea fallosConsecutivos a 0
        val ctxExito = SyncIntervalPolicy.SyncContext(fallosConsecutivos = 0, esSemanaActivaOverride = true)
        assertEquals(Duration.ofMinutes(10), SyncIntervalPolicy.proximoIntervalo(lunes10am, ctxExito))
    }

    @Test
    fun sesionExpiradaOFormatoInesperado_retornaZeroParaNoInsistirEnBucle() {
        val lunes10am = ZonedDateTime.of(2026, 4, 13, 10, 0, 0, 0, limaZone)
        val ctxFatal = SyncIntervalPolicy.SyncContext(
            fallosConsecutivos = 0,
            esErrorFatal = true
        )

        val intervalo = SyncIntervalPolicy.proximoIntervalo(lunes10am, ctxFatal)
        assertTrue(intervalo.isZero)
    }
}
