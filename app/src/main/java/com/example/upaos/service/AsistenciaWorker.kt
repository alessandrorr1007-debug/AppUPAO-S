package com.example.upaos.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.AsistenciaResponse
import com.example.upaos.data.model.LoginRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

class AsistenciaWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationPrefs = NotificationPreferences(context)
    private val tokenManager = TokenManager(context)
    private val apiCache = ApiCache(context)
    private val gson = Gson()

    override suspend fun doWork(): Result {
        if (!notificationPrefs.checkAsistenciaEnabled) {
            Log.d(TAG, "Revisión de asistencia desactivada por el usuario.")
            return Result.success()
        }

        val user = tokenManager.getSavedUser()
        val pass = tokenManager.getSavedPass()
        var token = tokenManager.getToken()

        if (user.isNullOrBlank()) {
            Log.d(TAG, "Sin usuario guardado, omitiendo chequeo.")
            return Result.success()
        }

        try {
            // Si no hay token pero sí credenciales, intentamos autenticar
            if (token.isNullOrBlank() && !pass.isNullOrBlank()) {
                val loginRes = RetrofitClient.apiService.login(LoginRequest(user, pass))
                val nuevoToken = loginRes.body()?.token
                if (loginRes.isSuccessful && !nuevoToken.isNullOrBlank()) {
                    token = nuevoToken
                    tokenManager.saveToken(nuevoToken)
                }
            }

            if (token.isNullOrBlank()) {
                Log.w(TAG, "No se pudo obtener token para revisión de asistencia.")
                return Result.success()
            }

            // Consultamos la asistencia
            var res = RetrofitClient.apiService.getAsistencia("Bearer $token")

            // Si expiró el token y tenemos credenciales, reintentamos login
            if (res.code() == 401 && !pass.isNullOrBlank()) {
                val loginRes = RetrofitClient.apiService.login(LoginRequest(user, pass))
                val nuevoToken = loginRes.body()?.token
                if (loginRes.isSuccessful && !nuevoToken.isNullOrBlank()) {
                    token = nuevoToken
                    tokenManager.saveToken(nuevoToken)
                    res = RetrofitClient.apiService.getAsistencia("Bearer $token")
                }
            }

            if (res.isSuccessful && res.body() != null) {
                val nuevaAsistencia = res.body()!!.asistencia
                compararYNotificar(nuevaAsistencia)

                // Guardamos en caché y actualizamos fecha de última revisión
                val cacheKey = "asistencia_$user"
                apiCache.guardar(cacheKey, gson.toJson(res.body()!!))
                notificationPrefs.ultimaRevisionAsistencia = System.currentTimeMillis()
                Log.d(TAG, "Revisión de asistencia completada con éxito.")
                return Result.success()
            } else {
                Log.w(TAG, "Respuesta no exitosa al consultar asistencia: ${res.code()}")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en AsistenciaWorker: ${e.localizedMessage}", e)
            return Result.retry()
        }
    }

    private fun compararYNotificar(nuevosCursos: List<AsistenciaCurso>) {
        val snapshotPrevioRaw = notificationPrefs.ultimoSnapshotAsistencia
        val type = object : TypeToken<Map<String, SnapshotCurso>>() {}.type
        val snapshotPrevio: Map<String, SnapshotCurso> = if (!snapshotPrevioRaw.isNullOrBlank()) {
            try {
                gson.fromJson(snapshotPrevioRaw, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val nuevoSnapshot = mutableMapOf<String, SnapshotCurso>()

        for (curso in nuevosCursos) {
            val key = curso.crn?.takeIf { it.isNotBlank() }
                ?: curso.codigoMateria?.takeIf { it.isNotBlank() }
                ?: curso.displayNombre

            val faltasActuales = curso.faltas ?: 0
            val pctActual = curso.porcentaje ?: 0.0
            val asistenciasActuales = curso.vecesAsistidas

            val tipoTag = when {
                !curso.tipo.isNullOrBlank() -> " (${curso.tipo})"
                !curso.tipoComponente.isNullOrBlank() -> " (${curso.tipoComponente})"
                !curso.seccion.isNullOrBlank() -> " (Sec. ${curso.seccion})"
                else -> ""
            }
            val nombreConTipo = "${curso.displayNombre}$tipoTag"

            nuevoSnapshot[key] = SnapshotCurso(
                nombre = nombreConTipo,
                faltas = curso.faltas,
                asistencias = asistenciasActuales,
                porcentaje = curso.porcentaje
            )

            // Solo comparamos si ya existía un snapshot previo (evitamos spam en el 1er arranque)
            if (snapshotPrevio.isNotEmpty()) {
                val previo = snapshotPrevio[key]
                if (previo != null) {
                    val faltasPrevias = previo.faltas ?: 0
                    val asistPrevias = previo.asistencias

                    if (faltasActuales > faltasPrevias) {
                        val dif = faltasActuales - faltasPrevias
                        val plural = if (dif == 1) "una nueva falta" else "$dif faltas nuevas"
                        val titulo = "⚠️ Falta registrada"
                        val mensaje = "$nombreConTipo: Te registraron $plural (Total: $faltasActuales). Asistencia: ${formatearPct(pctActual)}%."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                    } else if (asistenciasActuales != null && asistPrevias != null && asistenciasActuales > asistPrevias) {
                        val dif = asistenciasActuales - asistPrevias
                        val plural = if (dif == 1) "asistencia" else "$dif asistencias"
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró tu $plural! Llevas $asistenciasActuales asistencias."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                    } else if (previo.porcentaje != null && pctActual > previo.porcentaje && faltasActuales == faltasPrevias) {
                        // El porcentaje subió manteniendo las mismas faltas = nueva asistencia
                        val titulo = "✅ Asistencia registrada"
                        val mensaje = "$nombreConTipo: ¡Se registró una nueva asistencia! (${formatearPct(pctActual)}%)."
                        NotificationService.mostrarNotificacionAsistencia(applicationContext, titulo, mensaje)
                    }
                }
            }
        }

        // Guardamos el nuevo estado
        notificationPrefs.ultimoSnapshotAsistencia = gson.toJson(nuevoSnapshot)
    }

    private fun formatearPct(pct: Double): String =
        if (pct % 1.0 == 0.0) pct.toInt().toString() else "%.1f".format(pct)

    private data class SnapshotCurso(
        val nombre: String,
        val faltas: Int?,
        val asistencias: Int?,
        val porcentaje: Double?
    )

    companion object {
        private const val TAG = "UPAO_AsistenciaWorker"
        private const val UNIQUE_WORK_NAME = "upao_asistencia_check"

        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // WorkManager impone un mínimo de 15 minutos para PeriodicWorkRequest
            val minInterval = intervalMinutes.coerceAtLeast(15)

            val request = PeriodicWorkRequestBuilder<AsistenciaWorker>(
                minInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Programada revisión de asistencia cada $minInterval minutos.")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d(TAG, "Cancelada revisión periódica de asistencia.")
        }

        fun runOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AsistenciaWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
