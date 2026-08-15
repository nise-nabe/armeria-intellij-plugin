package com.linecorp.intellij.plugins.armeria.inspection

internal enum class ArmeriaClientDecoratorKind {
    LOGGING,
    RETRYING,
    CIRCUIT_BREAKER,
    ;

    companion object {
        fun fromSimpleName(simpleName: String): ArmeriaClientDecoratorKind? =
            when (simpleName) {
                "LoggingClient" -> LOGGING
                "RetryingClient", "RetryingRpcClient" -> RETRYING
                "CircuitBreakerClient" -> CIRCUIT_BREAKER
                else -> null
            }
    }
}
