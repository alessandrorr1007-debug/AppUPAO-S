package com.example.upaos.data.model

import java.util.UUID

data class TaskModel(
    val id: String = UUID.randomUUID().toString(),
    val usuario: String,
    val titulo: String,
    val curso: String = "",
    val descripcion: String = "",
    val fechaEntregaMillis: Long,
    val recordatorioMinutosAntes: Int = 1440, // -1: Desactivado, 60: 1 hora antes, 1440: 24 horas antes, 2880: 2 días antes
    val completada: Boolean = false,
    val tipo: String = "TAREA", // "TAREA" o "EXAMEN"
    val fechaCreacionMillis: Long = System.currentTimeMillis()
)
