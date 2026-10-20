package com.example.upaos.data.local

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

object BiometricHelper {

    enum class Availability(val message: String) {
        AVAILABLE("Usa biometría o la credencial del dispositivo."),
        NOT_CONFIGURED("No hay una credencial de desbloqueo configurada."),
        TEMPORARILY_UNAVAILABLE("La autenticación no está disponible temporalmente. Reintenta o usa tu credencial."),
        LOCKED_OUT("La biometría está bloqueada temporalmente. Usa la credencial del dispositivo o reautentícate en UPAO."),
        UNAVAILABLE("La autenticación no está disponible. Puedes reautenticarte manualmente en UPAO.")
    }

    fun availability(context: Context): Availability {
        val authenticators = if (Build.VERSION.SDK_INT >= 30)
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_CONFIGURED
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.TEMPORARILY_UNAVAILABLE
            else -> Availability.UNAVAILABLE
        }
    }

    /**
     * Verifica si el dispositivo cuenta con hardware y credenciales configuradas
     * (biometría fuerte o PIN/patrón/contraseña de pantalla).
     */
    fun puedeAutenticar(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            // En API < 30, verificar si tiene biometría o al menos bloqueo de pantalla configurado
            val biometricOk = biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            val deviceLockOk = keyguardManager?.isDeviceSecure ?: false
            biometricOk || deviceLockOk
        }
    }

    /**
     * Construye la información del prompt biométrico según la versión de Android
     * para evitar crashes por flags incompatibles en API < 30.
     */
    fun crearPromptInfo(): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear UPAO S")
            .setSubtitle("Autentícate para acceder a tu información")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            // En API < 30, setDeviceCredentialAllowed(true) es el método compatible
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        return builder.build()
    }
}
