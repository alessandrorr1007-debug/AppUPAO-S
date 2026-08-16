package com.example.upaos.ui.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val cuentas: List<AdminCuentaItem> = emptyList(),
    val sugerencias: List<AdminSugerenciaItem> = emptyList(),
    val metricas: AdminMetricasResponse? = null,
    val semanaInfo: SemanaInfo? = null,
    val mensajeExito: String? = null,
    val cuentaDetalle: AdminCuentaDetalle? = null,
    val cuentaDetalleCargando: Boolean = false,
    val actualizandoNotas: Boolean = false
)

class AdminViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun cargarDatosAdmin(token: String, adminUsuario: String = "000000000") {
        _uiState.value = _uiState.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val auth = "Bearer $token"

                val cuentasRes = api.getAdminCuentas(auth, adminUsuario)
                val sugerenciasRes = api.getAdminSugerencias(auth, adminUsuario)
                val metricasRes = api.getAdminMetricas(auth, adminUsuario)
                val semanaRes = api.getSemana()

                val cuentas = if (cuentasRes.isSuccessful) cuentasRes.body()?.cuentas.orEmpty() else emptyList()
                val sugerencias = if (sugerenciasRes.isSuccessful) sugerenciasRes.body()?.sugerencias.orEmpty() else emptyList()
                val metricas = if (metricasRes.isSuccessful) metricasRes.body() else null
                val semana = if (semanaRes.isSuccessful) semanaRes.body() else null

                _uiState.value = AdminUiState(
                    cargando = false,
                    cuentas = cuentas,
                    sugerencias = sugerencias,
                    metricas = metricas,
                    semanaInfo = semana
                )
            } catch (e: Exception) {
                Log.e("UPAO_APP", "Error cargando panel admin: ${e.localizedMessage}", e)
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = "Error al conectar con el servidor: ${e.localizedMessage}"
                )
            }
        }
    }

    fun cambiarEstadoSugerencia(id: Long, nuevoEstado: String, token: String, adminUsuario: String = "000000000") {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val res = api.updateAdminSugerenciaEstado(id, AdminEstadoSugerenciaRequest(nuevoEstado), "Bearer $token", adminUsuario)
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        sugerencias = _uiState.value.sugerencias.map { s ->
                            if (s.id == id) s.copy(estado = nuevoEstado) else s
                        },
                        mensajeExito = "Estado actualizado a $nuevoEstado"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = "No se pudo actualizar el estado (${res.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error de conexión: ${e.localizedMessage}")
            }
        }
    }

    fun guardarNotaSugerencia(id: Long, nota: String, token: String, adminUsuario: String = "000000000") {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val estadoActual = _uiState.value.sugerencias.firstOrNull { it.id == id }?.estado ?: return@launch
                val res = api.updateAdminSugerenciaEstado(
                    id,
                    AdminEstadoSugerenciaRequest(estado = estadoActual, notaAdmin = nota),
                    "Bearer $token",
                    adminUsuario
                )
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        sugerencias = _uiState.value.sugerencias.map { s ->
                            if (s.id == id) s.copy(notaAdmin = nota) else s
                        },
                        mensajeExito = "Nota del admin guardada"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = "No se pudo guardar la nota (${res.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error de conexión: ${e.localizedMessage}")
            }
        }
    }

    fun guardarFechaSemana(fechaInicioIso: String, token: String, adminUsuario: String = "000000000") {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val res = api.setAdminSemana(AdminSemanaRequest(fechaInicioIso), "Bearer $token", adminUsuario)
                if (res.isSuccessful && res.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        semanaInfo = res.body(),
                        mensajeExito = "Fecha de inicio de ciclo actualizada correctamente"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = "No se pudo guardar la fecha (${res.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error de conexión: ${e.localizedMessage}")
            }
        }
    }

    fun limpiarMensajeExito() {
        _uiState.value = _uiState.value.copy(mensajeExito = null)
    }

    fun limpiarError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun buscarCuentaDetalle(usuario: String, token: String, adminUsuario: String = "000000000") {
        if (usuario.isBlank()) return
        _uiState.value = _uiState.value.copy(cuentaDetalleCargando = true, cuentaDetalle = null)
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.getAdminCuentaDetalle(usuario, "Bearer $token", adminUsuario)
                if (res.isSuccessful && res.body() != null) {
                    _uiState.value = _uiState.value.copy(cuentaDetalle = res.body(), cuentaDetalleCargando = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        cuentaDetalleCargando = false,
                        error = if (res.code() == 404) "Usuario $usuario no encontrado" else "No se pudo cargar el detalle (${res.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cuentaDetalleCargando = false,
                    error = "Error de conexión: ${e.localizedMessage}"
                )
            }
        }
    }

    fun limpiarCuentaDetalle() {
        _uiState.value = _uiState.value.copy(cuentaDetalle = null, cuentaDetalleCargando = false)
    }

    fun actualizarNotasCuenta(usuario: String, token: String, adminUsuario: String = "000000000") {
        _uiState.value = _uiState.value.copy(actualizandoNotas = true)
        viewModelScope.launch {
            try {
                val res = RetrofitClient.apiService.actualizarNotasAdmin(usuario, "Bearer $token", adminUsuario)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    val detalle = _uiState.value.cuentaDetalle
                    _uiState.value = _uiState.value.copy(
                        actualizandoNotas = false,
                        cuentaDetalle = detalle?.copy(ultimaRevision = null),
                        mensajeExito = if (body.success) {
                            if (body.totalCambios > 0) "Notas actualizadas (${body.totalCambios} cambio(s)) en ${body.periodo}" else "Notas actualizadas. Sin cambios en ${body.periodo}"
                        } else {
                            body.message ?: "No se pudo actualizar las notas"
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        actualizandoNotas = false,
                        error = "No se pudo actualizar las notas (${res.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    actualizandoNotas = false,
                    error = "Error de conexión: ${e.localizedMessage}"
                )
            }
        }
    }
}
