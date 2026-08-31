package com.linecorp.intellij.plugins.armeria.explorer.model

import com.linecorp.intellij.plugins.armeria.message

object GrpcRoutePath {
    private val METHOD_PATH = Regex("""^/[^/]+/[^/]+$""")

    fun path(
        fqService: String,
        methodName: String,
    ): String = "/$fqService/$methodName"

    fun isMethodPath(path: String): Boolean = METHOD_PATH.matches(path)
}

/** Stable Route Explorer tokens for gRPC builder options (not localized labels). */
object GrpcRouteHint {
    const val UNFRAMED = "grpc-unframed"
    const val REFLECTION = "grpc-reflection"

    fun presentable(hint: String): String =
        when (hint) {
            UNFRAMED -> message("route.explorer.badge.grpcUnframed")
            REFLECTION -> message("route.explorer.badge.grpcReflection")
            else -> hint
        }

    fun isBadge(hint: String): Boolean = hint == UNFRAMED || hint == REFLECTION
}
