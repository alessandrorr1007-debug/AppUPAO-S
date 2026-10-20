package com.example.upaos.data.refresh

import kotlinx.coroutines.CancellationException
import java.io.IOException

data class RefreshIssue(val source: String, val cause: Exception)

sealed interface RefreshResult<out T> {
    data class Complete<T>(val value: T) : RefreshResult<T>
    data class Partial<T>(val value: T, val issues: List<RefreshIssue>) : RefreshResult<T>
    data class Error(val issue: RefreshIssue) : RefreshResult<Nothing>
}

class IncompleteRefreshException(val issues: List<RefreshIssue>) : IOException(
    "Actualización incompleta: " + issues.joinToString { it.source }, issues.firstOrNull()?.cause
)

suspend fun <T> captureRefresh(source: String, load: suspend () -> T): RefreshResult<T> = try {
    RefreshResult.Complete(load())
} catch (e: CancellationException) { throw e }
catch (e: Exception) { RefreshResult.Error(RefreshIssue(source, e)) }

fun <T> RefreshResult<T>.requireComplete(): T = when (this) {
    is RefreshResult.Complete -> value
    is RefreshResult.Partial -> throw IncompleteRefreshException(issues)
    is RefreshResult.Error -> throw issue.cause
}

fun <T, R> RefreshResult<T>.map(transform: (T) -> R): RefreshResult<R> = when (this) {
    is RefreshResult.Complete -> RefreshResult.Complete(transform(value))
    is RefreshResult.Partial -> RefreshResult.Partial(transform(value), issues)
    is RefreshResult.Error -> this
}

fun <T> combineRefreshes(results: List<RefreshResult<T>>): RefreshResult<List<T>> {
    val values = mutableListOf<T>()
    val issues = mutableListOf<RefreshIssue>()
    for (result in results) when (result) {
        is RefreshResult.Complete -> values += result.value
        is RefreshResult.Partial -> { values += result.value; issues += result.issues }
        is RefreshResult.Error -> issues += result.issue
    }
    return when {
        issues.isEmpty() -> RefreshResult.Complete(values)
        values.isEmpty() -> RefreshResult.Error(RefreshIssue("Actualización", IncompleteRefreshException(issues)))
        else -> RefreshResult.Partial(values, issues)
    }
}

/** Persistence and freshness changes are allowed only after a complete read. */
suspend fun <T> publishComplete(result: RefreshResult<T>, publish: suspend (T) -> Unit): T {
    val value = result.requireComplete()
    publish(value)
    return value
}

suspend fun runSyncChecks(checks: List<suspend () -> RefreshResult<Unit>>): RefreshResult<Unit> =
    combineRefreshes(checks.map { it() }).map { Unit }
