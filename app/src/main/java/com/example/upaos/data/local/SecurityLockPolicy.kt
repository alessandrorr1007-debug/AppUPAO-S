package com.example.upaos.data.local

/**
 * Lógica pura y desacoplada para decidir si la aplicación debe bloquearse.
 */
object SecurityLockPolicy {
    /** Tiempo máximo de gracia en segundo plano antes de requerir re-autenticación (1 minuto = 60000 ms). */
    const val TIMEOUT_MILLIS = 60_000L

    /**
     * Determina si la app debe bloquearse y requerir desbloqueo biométrico o PIN.
     *
     * @param ultimaVezEnSegundoPlano timestamp en milisegundos cuando la app pasó a segundo plano, o null si es arranque en frío.
     * @param ahora timestamp actual en milisegundos.
     * @param opcionActiva si la opción de "Bloqueo de la app" está activada en ajustes.
     * @param yaAutenticadoEnEstaSesion si el usuario ya se autenticó exitosamente durante este inicio de proceso.
     */
    fun debeBloquear(
        ultimaVezEnSegundoPlano: Long?,
        ahora: Long,
        opcionActiva: Boolean,
        yaAutenticadoEnEstaSesion: Boolean = false
    ): Boolean {
        if (!opcionActiva) return false
        if (ultimaVezEnSegundoPlano == null) {
            return !yaAutenticadoEnEstaSesion
        }
        val tiempoEnSegundoPlano = ahora - ultimaVezEnSegundoPlano
        return tiempoEnSegundoPlano > TIMEOUT_MILLIS
    }
}

/** Only a verified authentication event can release the gate, never availability checks. */
sealed interface UnlockEvent {
    data object Unavailable : UnlockEvent
    data object Failed : UnlockEvent
    data object DeviceAuthenticated : UnlockEvent
    data class UpaoAuthenticated(val expectedUser: String?, val actualUser: String) : UnlockEvent
}

fun remainsLocked(event: UnlockEvent): Boolean = when (event) {
    UnlockEvent.Unavailable, UnlockEvent.Failed -> true
    UnlockEvent.DeviceAuthenticated -> false
    is UnlockEvent.UpaoAuthenticated -> event.expectedUser.isNullOrBlank() || event.expectedUser != event.actualUser
}
