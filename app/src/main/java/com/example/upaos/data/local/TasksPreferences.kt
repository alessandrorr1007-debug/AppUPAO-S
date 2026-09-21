package com.example.upaos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.upaos.data.model.TaskModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tasksDataStore by preferencesDataStore(name = "tasks_preferences")

class TasksPreferences(private val context: Context) {

    private val gson = Gson()

    private fun getKey(usuario: String) = stringPreferencesKey("tasks_user_${usuario.trim().lowercase()}")

    fun getTasksFlow(usuario: String): Flow<List<TaskModel>> {
        val key = getKey(usuario)
        return context.tasksDataStore.data.map { prefs ->
            val json = prefs[key]
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<TaskModel>>() {}.type
                    gson.fromJson<List<TaskModel>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    suspend fun getTasks(usuario: String): List<TaskModel> {
        return getTasksFlow(usuario).first()
    }

    suspend fun saveTask(task: TaskModel) {
        val currentList = getTasks(task.usuario).toMutableList()
        val index = currentList.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            currentList[index] = task
        } else {
            currentList.add(0, task)
        }
        guardarLista(task.usuario, currentList)
    }

    suspend fun deleteTask(taskId: String, usuario: String) {
        val currentList = getTasks(usuario).filterNot { it.id == taskId }
        guardarLista(usuario, currentList)
    }

    suspend fun toggleTaskCompleted(taskId: String, usuario: String) {
        val currentList = getTasks(usuario).map {
            if (it.id == taskId) it.copy(completada = !it.completada) else it
        }
        guardarLista(usuario, currentList)
    }

    private suspend fun guardarLista(usuario: String, lista: List<TaskModel>) {
        val key = getKey(usuario)
        val json = gson.toJson(lista)
        context.tasksDataStore.edit { prefs ->
            prefs[key] = json
        }
    }
}
