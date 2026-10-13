package com.example.upaos

import com.example.upaos.data.api.mensajeErrorLogin
import com.example.upaos.data.api.requiereCaptcha
import com.example.upaos.data.model.LoginResponse
import org.junit.Assert.*
import org.junit.Test

class LoginErrorsTest {
    @Test fun invalidCredentialsNeverOpenCaptcha() {
        val response = LoginResponse(false, null, true, "image", "Error", "invalid_credentials")
        assertFalse(requiereCaptcha(response))
        assertEquals("ID o contraseña incorrectos. Revisa tus datos.", mensajeErrorLogin(200, response))
    }

    @Test fun genuineChallengeStillOpensCaptcha() {
        assertTrue(requiereCaptcha(LoginResponse(false, null, true, "image", null, "captcha_required", "challenge")))
        assertFalse(requiereCaptcha(LoginResponse(false, null, true, "", null)))
    }

    @Test fun legacyInvalidCredentialsResponseNeverOpensCaptcha() {
        val response = LoginResponse(false, null, true, "image", "Código o credenciales incorrectos.")
        assertFalse(requiereCaptcha(response))
        assertEquals("ID o contraseña incorrectos. Revisa tus datos.", mensajeErrorLogin(200, response))
    }

    @Test fun administrator401HasHelpfulMessage() {
        assertEquals("ID o contraseña incorrectos. Revisa tus datos.", mensajeErrorLogin(401, null))
    }

    @Test fun serverOutageDoesNotBlameCredentials() {
        val message = mensajeErrorLogin(503, null)
        assertTrue(message.contains("conectar con UPAO"))
        assertFalse(message.contains("incorrectos"))
    }

    @Test fun errorBodyAndMalformedResponseHaveSafeFallback() {
        assertEquals("El código venció", mensajeErrorLogin(400, null, "{\"detail\":\"El código venció\"}"))
        assertTrue(mensajeErrorLogin(400, null, "<html>Error</html>").contains("No se pudo iniciar sesión"))
    }
}
