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
    val mensajeExito: String? = null
)

class AdminViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun cargarDatosAdmin(adminUsuario: String = "000279330") {
        _uiState.value = _uiState.value.copy(cargando = true, error = null)
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService

                val cuentasRes = api.getAdminCuentas(adminUsuario)
                val sugerenciasRes = api.getAdminSugerencias(adminUsuario)
                val metricasRes = api.getAdminMetricas(adminUsuario)
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

    fun cambiarEstadoSugerencia(id: Long, nuevoEstado: String, adminUsuario: String = "000279330") {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val res = api.updateAdminSugerenciaEstado(id, AdminEstadoSugerenciaRequest(nuevoEstado), adminUsuario)
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

    fun guardarFechaSemana(fechaInicioIso: String, adminUsuario: String = "000279330") {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.apiService
                val res = api.setAdminSemana(AdminSemanaRequest(fechaInicioIso), adminUsuario)
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
}
