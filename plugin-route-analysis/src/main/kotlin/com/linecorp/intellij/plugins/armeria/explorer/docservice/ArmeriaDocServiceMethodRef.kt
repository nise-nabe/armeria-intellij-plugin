package com.linecorp.intellij.plugins.armeria.explorer.docservice

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol

/**
 * DocService debug-form coordinates for a route (`serviceName` + `methodName`).
 *
 * Annotated services use the fully qualified class name and Java/Kotlin method name.
 * gRPC uses the protobuf service and RPC names. Thrift uses the IDL service/method names.
 */
data class ArmeriaDocServiceMethodRef(
    val serviceName: String,
    val methodName: String,
) {
    companion object {
        fun from(route: ArmeriaRoute): ArmeriaDocServiceMethodRef? =
            when (route.routeMatch) {
                RouteMatch.ANNOTATED_HTTP -> fromAnnotatedTarget(route.target)
                RouteMatch.RUNTIME ->
                    fromRuntimeTarget(route.target)
                        ?: fromGrpcPath(route.path)
                        ?: fromAnnotatedTarget(route.target)
                RouteMatch.NON_HTTP ->
                    when {
                        isProtocol(route, RouteProtocol.GRPC) ->
                            fromGrpcPath(route.path) ?: fromDottedTarget(route.target)
                        isProtocol(route, RouteProtocol.THRIFT) -> fromDottedTarget(route.target)
                        else -> null
                    }
                else -> null
            }

        internal fun fromAnnotatedTarget(target: String): ArmeriaDocServiceMethodRef? {
            val hash = target.lastIndexOf('#')
            if (hash <= 0 || hash == target.lastIndex) {
                return null
            }
            val serviceName = target.substring(0, hash).trim()
            val methodName =
                target
                    .substring(hash + 1)
                    .substringBefore('(')
                    .trim()
            return methodRef(serviceName, methodName)
        }

        internal fun fromGrpcPath(path: String): ArmeriaDocServiceMethodRef? {
            val trimmed = path.trim().trim('/')
            val slash = trimmed.lastIndexOf('/')
            if (slash <= 0 || slash == trimmed.lastIndex) {
                return null
            }
            return methodRef(trimmed.substring(0, slash), trimmed.substring(slash + 1))
        }

        internal fun fromRuntimeTarget(target: String): ArmeriaDocServiceMethodRef? {
            val slash = target.lastIndexOf('/')
            if (slash <= 0 || slash == target.lastIndex) {
                return null
            }
            return methodRef(target.substring(0, slash), target.substring(slash + 1))
        }

        internal fun fromDottedTarget(target: String): ArmeriaDocServiceMethodRef? {
            val dot = target.lastIndexOf('.')
            if (dot <= 0 || dot == target.lastIndex) {
                return null
            }
            return methodRef(target.substring(0, dot), target.substring(dot + 1))
        }

        private fun methodRef(
            serviceName: String,
            methodName: String,
        ): ArmeriaDocServiceMethodRef? {
            if (serviceName.isBlank() || methodName.isBlank()) {
                return null
            }
            return ArmeriaDocServiceMethodRef(serviceName, methodName)
        }

        private fun isProtocol(
            route: ArmeriaRoute,
            protocol: RouteProtocol,
        ): Boolean = route.protocol.equals(protocol.presentableName(), ignoreCase = true)
    }
}
