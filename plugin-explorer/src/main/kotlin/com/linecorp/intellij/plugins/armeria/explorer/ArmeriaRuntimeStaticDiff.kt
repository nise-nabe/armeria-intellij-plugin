package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport

internal object ArmeriaRuntimeStaticDiff {
    data class Summary(
        val sharedCount: Int,
        val runtimeOnlyCount: Int,
        val staticOnlyCount: Int,
    )

    fun summarize(
        staticRoutes: List<ArmeriaRoute>,
        runtimeRoutes: List<ArmeriaRoute>,
    ): Summary = compare(staticRoutes, runtimeRoutes).summary()

    fun compare(
        staticRoutes: List<ArmeriaRoute>,
        runtimeRoutes: List<ArmeriaRoute>,
    ): Comparison =
        Comparison(
            staticCandidates =
                staticRoutes
                    .filter { it.routeMatch in STATIC_ENDPOINT_MATCHES }
                    .map(::canonicalRoute)
                    .distinct(),
            runtimeCandidates =
                runtimeRoutes
                    .filter { it.routeMatch == RouteMatch.RUNTIME }
                    .map(::canonicalRoute)
                    .distinct(),
        )

    class Comparison internal constructor(
        private val staticCandidates: List<CanonicalRoute>,
        private val runtimeCandidates: List<CanonicalRoute>,
    ) {
        fun isStaticOnly(route: ArmeriaRoute): Boolean {
            if (route.routeMatch !in STATIC_ENDPOINT_MATCHES) {
                return false
            }
            val static = canonicalRoute(route)
            return runtimeCandidates.none { matches(static = static, runtime = it) }
        }

        fun isRuntimeOnly(route: ArmeriaRoute): Boolean {
            if (route.routeMatch != RouteMatch.RUNTIME) {
                return false
            }
            val runtime = canonicalRoute(route)
            return staticCandidates.none { matches(static = it, runtime = runtime) }
        }

        fun summary(): Summary {
            val sharedRuntimeCount = runtimeCandidates.count { runtime -> staticCandidates.any { matches(static = it, runtime = runtime) } }
            val sharedStaticCount = staticCandidates.count { static -> runtimeCandidates.any { matches(static = static, runtime = it) } }
            return Summary(
                sharedCount = sharedRuntimeCount,
                runtimeOnlyCount = runtimeCandidates.size - sharedRuntimeCount,
                staticOnlyCount = staticCandidates.size - sharedStaticCount,
            )
        }
    }

    private val STATIC_ENDPOINT_MATCHES =
        setOf(
            RouteMatch.ANNOTATED_HTTP,
            RouteMatch.ANNOTATED_SERVICE,
            RouteMatch.SERVICE,
            RouteMatch.SERVICE_UNDER,
            RouteMatch.FILE_SERVICE,
            RouteMatch.HEALTH_CHECK,
            RouteMatch.ROUTE_FLUENT,
            RouteMatch.DELEGATED,
            RouteMatch.CONFIG,
        )

    data class CanonicalRoute(
        val pathShape: String,
        val methods: Set<String>,
        val mountPrefixShape: String?,
    )

    private fun canonicalRoute(route: ArmeriaRoute): CanonicalRoute {
        val normalizedPath = if (route.path.length > 1) route.path.trimEnd('/') else route.path
        val pathShape =
            trimTrailingSlash(
                ArmeriaPathVariableSupport.pathShapeForOverlap(
                    path = normalizedPath,
                    explicitExactPath = route.explicitExactPath,
                    pathType = route.pathType,
                ),
            )
        return CanonicalRoute(
            pathShape = pathShape,
            methods = methods(route.httpMethod),
            mountPrefixShape = mountPrefixShape(route, pathShape),
        )
    }

    private fun mountPrefixShape(
        route: ArmeriaRoute,
        pathShape: String,
    ): String? {
        if (route.pathType == PathType.GLOB && pathShape.endsWith("/**")) {
            val globPrefix = pathShape.removeSuffix("/**").removePrefix("glob:")
            return trimTrailingSlash(
                ArmeriaPathVariableSupport.pathShapeForOverlap(
                    path = globPrefix,
                    explicitExactPath = false,
                    pathType = PathType.EXACT,
                ),
            )
        }
        return if (
            route.pathType == PathType.PREFIX ||
            route.routeMatch == RouteMatch.SERVICE_UNDER ||
            route.routeMatch == RouteMatch.FILE_SERVICE ||
            route.annotatedServiceHasPathPrefix
        ) {
            pathShape
        } else {
            null
        }
    }

    private fun methods(httpMethod: String): Set<String> =
        httpMethod
            .split(',', ' ')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun matches(
        static: CanonicalRoute,
        runtime: CanonicalRoute,
    ): Boolean {
        if (!methodsOverlap(static.methods, runtime.methods)) {
            return false
        }
        if (static.pathShape == runtime.pathShape) {
            return true
        }
        val mountPrefixShape = static.mountPrefixShape ?: return false
        val prefix = mountPrefixShape.trimEnd('/')
        return runtime.pathShape.startsWith("$prefix/") || runtime.pathShape == prefix
    }

    private fun methodsOverlap(
        staticMethods: Set<String>,
        runtimeMethods: Set<String>,
    ): Boolean =
        staticMethods.isEmpty() ||
            runtimeMethods.isEmpty() ||
            staticMethods.intersect(runtimeMethods).isNotEmpty()

    private fun trimTrailingSlash(path: String): String = if (path.length > 1) path.trimEnd('/') else path
}
