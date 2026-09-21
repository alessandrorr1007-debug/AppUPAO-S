package com.example.upaos.data.model

import java.util.UUID

data class TaskModel(
    val id: String = UUID.randomUUID().toString(),
    val usuario: String,
    val titulo: String,
    val curso: String = "",
    val descripcion: String = "",
    val fechaEntregaMillis: Long,
    val recordatorioFechaHoraMillis: Long? = null, // Fecha y hora exacta programada para el aviso en el celular
    val recordatorioMinutosAntes: Int = -1, // retrocompatibilidad
    val completada: Boolean = false,
    val tipo: String = "TAREA", // "TAREA" o "EXAMEN"
    val fechaCreacionMillis: Long = System.currentTimeMillis()
)
