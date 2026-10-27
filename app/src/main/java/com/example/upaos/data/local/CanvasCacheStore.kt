package com.example.upaos.data.local

import com.example.upaos.data.model.*

interface CanvasCacheStore {
    suspend fun guardar(user: String, items: List<CanvasTodoItem>): Unit
    suspend fun cargar(user: String): Pair<List<CanvasTodoItem>, Long?>
    suspend fun guardarConversaciones(user: String, convs: List<CanvasConversation>): Unit
    suspend fun cargarConversaciones(user: String): List<CanvasConversation>
    suspend fun guardarCursos(user: String, courses: List<CanvasCourse>): Unit
    suspend fun cargarCursos(user: String): List<CanvasCourse>
    suspend fun guardarNotificaciones(user: String, notifs: List<CanvasNotificationItem>): Unit
    suspend fun cargarNotificaciones(user: String): List<CanvasNotificationItem>
    suspend fun guardarGrupos(user: String, groups: List<CanvasGroup>): Unit
    suspend fun cargarGrupos(user: String): List<CanvasGroup>
    suspend fun guardarPendientesCurso(user: String, courseId: Long, items: List<CanvasTodoItem>): Unit
    suspend fun cargarPendientesPorCurso(user: String): Map<Long, List<CanvasTodoItem>>
    suspend fun limpiar(user: String): Unit
}
