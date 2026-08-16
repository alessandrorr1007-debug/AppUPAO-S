package com.example.upaos.data.api

import com.example.upaos.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("login/confirmar-captcha")
    suspend fun loginConfirmarCaptcha(@Body request: ManualCaptchaRequest): Response<LoginResponse>

    @GET("notas/periodos")
    suspend fun getPeriodos(
        @Header("Authorization") token: String? = null
    ): Response<PeriodosResponse>

    @GET("notas/carreras")
    suspend fun getCarreras(
        @Header("Authorization") token: String? = null,
        @Query("term") term: String = "202610"
    ): Response<CarrerasResponse>

    @POST("notas/buscar")
    suspend fun buscarNotas(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<GradesResponse>

    @POST("notas/detalle")
    suspend fun getDetalleCurso(
        @Header("Authorization") token: String,
        @Body request: Map<String, String>
    ): Response<DetalleCursoResponse>

    @PATCH("settings/auto-check")
    suspend fun updateAutoCheck(
        @Query("usuario") usuario: String,
        @Body request: AutoCheckRequest
    ): Response<Map<String, Any>>

    @PATCH("settings/intervalo")
    suspend fun updateIntervalo(
        @Query("usuario") usuario: String,
        @Body request: IntervaloRequest
    ): Response<Map<String, Any>>

    @GET("settings")
    suspend fun getSettings(
        @Query("usuario") usuario: String
    ): Response<SettingsResponse>

    @POST("device-token")
    suspend fun updateDeviceToken(
        @Query("usuario") usuario: String,
        @Body request: DeviceTokenRequest
    ): Response<Map<String, Any>>

    @POST("notas/actualizar-ahora")
    suspend fun actualizarAhora(
        @Query("usuario") usuario: String
    ): Response<Map<String, Any>>

    @GET("asistencia")
    suspend fun getAsistencia(
        @Header("Authorization") token: String
    ): Response<AsistenciaResponse>

    @GET("horario")
    suspend fun getHorario(
        @Header("Authorization") token: String,
        @Query("term") term: String
    ): Response<HorarioResponse>

    @GET("notificaciones")
    suspend fun getNotificaciones(
        @Query("usuario") usuario: String
    ): Response<NotificacionesResponse>

    @PATCH("notificaciones/marcar-leidas")
    suspend fun marcarNotificacionesLeidas(
        @Query("usuario") usuario: String
    ): Response<Map<String, Any>>

    @PATCH("notificaciones/{id}/marcar-leida")
    suspend fun marcarNotificacionLeida(
        @Path("id") id: Long,
        @Query("usuario") usuario: String
    ): Response<Map<String, Any>>

    @GET("cuenta")
    suspend fun getCuenta(
        @Query("usuario") usuario: String
    ): Response<CuentaInfo>

    @GET("semana")
    suspend fun getSemana(): Response<SemanaInfo>

    @POST("sugerencias")
    suspend fun postSugerencia(@Body request: SugerenciaRequest): Response<SugerenciaResponse>

    @GET("sugerencias/mis")
    suspend fun getMisSugerencias(
        @Query("usuario") usuario: String
    ): Response<MisSugerenciasResponse>

    @GET("api/promedio/{periodo}")
    suspend fun getPromedioPeriodo(
        @Path("periodo") periodo: String,
        @Header("Authorization") authorization: String
    ): Response<PromedioPeriodoResponse>

    // ---------- Endpoints Panel Administrador ----------

    @GET("admin/cuentas")
    suspend fun getAdminCuentas(
        @Header("Authorization") authorization: String,
        @Query("admin_usuario") adminUsuario: String = "000000000"
    ): Response<AdminCuentasResponse>

    @GET("admin/sugerencias")
    suspend fun getAdminSugerencias(
        @Header("Authorization") authorization: String,
        @Query("admin_usuario") adminUsuario: String = "000000000"
    ): Response<AdminSugerenciasResponse>

    @PATCH("admin/sugerencias/{id}/estado")
    suspend fun updateAdminSugerenciaEstado(
        @Path("id") id: Long,
        @Body request: AdminEstadoSugerenciaRequest,
        @Header("Authorization") authorization: String,
        @Query("admin_usuario") adminUsuario: String = "000000000"
    ): Response<Map<String, Any>>

    @POST("admin/semana")
    suspend fun setAdminSemana(
        @Body request: AdminSemanaRequest,
        @Header("Authorization") authorization: String,
        @Query("admin_usuario") adminUsuario: String = "000000000"
    ): Response<SemanaInfo>

    @GET("admin/metricas")
    suspend fun getAdminMetricas(
        @Header("Authorization") authorization: String,
        @Query("admin_usuario") adminUsuario: String = "000000000"
    ): Response<AdminMetricasResponse>
}
