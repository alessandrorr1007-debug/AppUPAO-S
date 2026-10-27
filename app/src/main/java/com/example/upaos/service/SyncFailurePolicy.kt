package com.example.upaos.service

import com.example.upaos.data.refresh.IncompleteRefreshException
import com.example.upaos.data.upao.*

internal fun isFatalSyncFailure(error: Throwable): Boolean = when (error) {
    is UpaoInvalidCredentialsException, is UpaoSessionExpiredException, is UpaoUnexpectedFormatException -> true
    is IncompleteRefreshException -> error.issues.any { isFatalSyncFailure(it.cause) }
    else -> error.cause?.takeIf { it !== error }?.let(::isFatalSyncFailure) ?: false
}
