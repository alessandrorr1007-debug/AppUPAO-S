package com.example.upaos

import com.example.upaos.data.local.SecurityLockPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLockPolicyTest {

    @Test
    fun cuandoOpcionEstaDesactivada_nuncaDebeBloquear() {
        val ahora = 100_000L
        // Arranque en frío
        assertFalse(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = null,
                ahora = ahora,
                opcionActiva = false,
                yaAutenticadoEnEstaSesion = false
            )
        )
        // Regreso de segundo plano tras 10 minutos
        assertFalse(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = ahora - 600_000L,
                ahora = ahora,
                opcionActiva = false
            )
        )
    }

    @Test
    fun enArranqueEnFrio_siOpcionActiva_debeBloquearSiNoEstaAutenticado() {
        val ahora = 50_000L
        assertTrue(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = null,
                ahora = ahora,
                opcionActiva = true,
                yaAutenticadoEnEstaSesion = false
            )
        )
    }

    @Test
    fun enArranqueEnFrio_siOpcionActiva_noDebeBloquearSiYaFueAutenticado() {
        val ahora = 50_000L
        assertFalse(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = null,
                ahora = ahora,
                opcionActiva = true,
                yaAutenticadoEnEstaSesion = true
            )
        )
    }

    @Test
    fun enSegundoPlanoMenosDeUnMinuto_noDebeBloquear() {
        val ahora = 100_000L
        // 30 segundos en segundo plano
        val hace30Segundos = ahora - 30_000L
        assertFalse(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = hace30Segundos,
                ahora = ahora,
                opcionActiva = true
            )
        )

        // Exactamente 60 segundos en segundo plano (dentro del límite de gracia)
        val hace60Segundos = ahora - 60_000L
        assertFalse(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = hace60Segundos,
                ahora = ahora,
                opcionActiva = true
            )
        )
    }

    @Test
    fun enSegundoPlanoMasDeUnMinuto_debeBloquear() {
        val ahora = 100_000L
        // 61 segundos en segundo plano
        val hace61Segundos = ahora - 61_000L
        assertTrue(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = hace61Segundos,
                ahora = ahora,
                opcionActiva = true
            )
        )

        // 5 minutos en segundo plano
        val hace5Minutos = ahora - 300_000L
        assertTrue(
            SecurityLockPolicy.debeBloquear(
                ultimaVezEnSegundoPlano = hace5Minutos,
                ahora = ahora,
                opcionActiva = true
            )
        )
    }
}
