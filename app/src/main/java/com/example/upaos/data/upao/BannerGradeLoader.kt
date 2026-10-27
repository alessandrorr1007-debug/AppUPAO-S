package com.example.upaos.data.upao

import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.DetalleCursoResponse
import com.example.upaos.data.model.SubComponente
import com.example.upaos.data.refresh.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException

internal fun parseBannerObjects(json: String): List<JsonObject> {
    try {
        val parsed = JsonParser.parseString(json)
        val array = when {
            parsed.isJsonArray -> parsed.asJsonArray
            parsed.isJsonObject && parsed.asJsonObject.get("data")?.isJsonArray == true -> parsed.asJsonObject.getAsJsonArray("data")
            parsed.isJsonObject && parsed.asJsonObject.get("items")?.isJsonArray == true -> parsed.asJsonObject.getAsJsonArray("items")
            else -> throw UpaoUnexpectedFormatException("Falta el arreglo esperado en Banner")
        }
        val items = array.map { if (it.isJsonObject) it.asJsonObject else throw UpaoUnexpectedFormatException("Elemento inválido en Banner") }
        val total = if (parsed.isJsonObject) parsed.asJsonObject.get("totalCount")?.takeUnless { it.isJsonNull }?.asInt else null
        if (total != null && total > items.size) throw IncompleteRefreshException(listOf(
            RefreshIssue("Páginas de Banner pendientes", java.io.IOException("La respuesta no contiene todos los registros declarados"))
        ))
        return items
    } catch (e: CancellationException) { throw e }
    catch (e: UpaoException) { throw e }
    catch (e: IncompleteRefreshException) { throw e }
    catch (e: Exception) { throw UpaoUnexpectedFormatException("No se pudo interpretar la respuesta de Banner", cause = e) }
}

internal suspend fun loadBannerGradeDetail(
    loadComponents: suspend () -> List<JsonObject>,
    parseComponent: (JsonObject) -> ComponenteDetalle,
    loadSubcomponents: suspend (Long) -> List<SubComponente>,
    periodo: String? = null
): RefreshResult<DetalleCursoResponse> {
    val components = when (val root = captureRefresh("Componentes de Banner", loadComponents)) {
        is RefreshResult.Complete -> root.value
        is RefreshResult.Error -> return root
        is RefreshResult.Partial -> error("El cargador raíz debe devolver una lista completa")
    }
    val results = components.map { json ->
        when (val parsed = captureRefresh("Componente de Banner") { parseComponent(json) }) {
            is RefreshResult.Complete -> {
                val component = parsed.value
                if (component.hasSubComponents) {
                    val id = component.componentId
                    if (id == null) RefreshResult.Partial(component, listOf(RefreshIssue("Subcomponentes", UpaoUnexpectedFormatException("Falta componentId"))))
                    else when (val subs = captureRefresh("Subcomponentes ${component.displayNombre}") { loadSubcomponents(id) }) {
                        is RefreshResult.Complete -> RefreshResult.Complete(component.copy(subcomponentes = subs.value))
                        is RefreshResult.Error -> RefreshResult.Partial(component, listOf(subs.issue))
                        is RefreshResult.Partial -> error("El cargador de subcomponentes debe devolver una lista completa")
                    }
                } else RefreshResult.Complete(component)
            }
            is RefreshResult.Error -> parsed
            is RefreshResult.Partial -> error("El parser debe devolver un componente completo")
        }
    }
    val combined = combineRefreshes(results)
    return combined.map { values ->
        val (projected, pending) = Calculators.calcularNotaProyectada(values, periodo)
        DetalleCursoResponse(success = combined is RefreshResult.Complete, totalCount = values.size,
            detalles = values, notaProyectada = projected, pesosPendientes = pending)
    }
}
