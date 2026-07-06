package com.linecorp.intellij.plugins.armeria.explorer

internal data class RegistrationChainStep(
    val methodName: String,
    val firstStringArg: String?,
    val rawMethodArgs: List<String>,
)

internal data class FluentRouteChainInfo(
    val httpMethod: String,
    val path: String,
    val pathType: PathType,
    val target: String,
)

internal data class RouteDecoratorChainInfo(
    val pathPattern: String,
    val pathType: PathType,
    val methods: String,
    val decoratorLabel: String,
)

internal object ArmeriaRegistrationChainReducer {

    fun reduceFluentRouteChain(
        stepsFromBuildUpward: List<RegistrationChainStep>,
        requireRouteAnchor: Boolean,
        handlerTarget: String?,
        defaultTarget: String,
    ): FluentRouteChainInfo? {
        var httpMethod = ""
        var path = "/"
        var pathType = PathType.EXACT
        var mountPrefix: String? = null
        var methodPath: String? = null
        var foundRoute = false
        var foundPath = false
        for (step in stepsFromBuildUpward) {
            when (step.methodName) {
                "route" -> {
                    foundRoute = true
                    break
                }
                in ServiceRegistrationMethod.FLUENT_ROUTE_HTTP_METHODS -> {
                    httpMethod = step.methodName.uppercase()
                    parsePathFromStep(step)?.let { parsed ->
                        methodPath = parsed.second
                        path = parsed.second
                        pathType = parsed.first
                        foundPath = true
                    }
                }
                "path" -> parsePathFromStep(step)?.let { parsed ->
                    methodPath = parsed.second
                    path = parsed.second
                    pathType = parsed.first
                    foundPath = true
                }
                "pathPrefix" -> {
                    val raw = step.firstStringArg ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("prefix:$raw")
                    mountPrefix = parsed.second
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "pathRegex" -> {
                    val raw = step.firstStringArg ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("regex:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "pathGlob" -> {
                    val raw = step.firstStringArg ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("glob:$raw")
                    pathType = parsed.first
                    path = parsed.second
                    foundPath = true
                }
                "methods", "method" -> {
                    httpMethod = step.rawMethodArgs.joinToString(", ") { argument ->
                        argument.removePrefix("HttpMethod.").uppercase()
                    }
                }
            }
        }
        if (requireRouteAnchor && !foundRoute) {
            return null
        }
        if (!requireRouteAnchor && !foundPath) {
            return null
        }
        val resolvedPath = if (mountPrefix != null && methodPath != null) {
            ArmeriaRouteSupport.combinePaths(mountPrefix!!, methodPath!!)
        } else {
            path
        }
        val resolvedPathType = if (mountPrefix != null && methodPath != null) {
            PathType.EXACT
        } else {
            pathType
        }
        return FluentRouteChainInfo(
            httpMethod = httpMethod,
            path = resolvedPath,
            pathType = resolvedPathType,
            target = handlerTarget ?: defaultTarget,
        )
    }

    fun reduceRouteDecoratorChain(
        steps: List<RegistrationChainStep>,
        defaultDecoratorLabel: String,
    ): RouteDecoratorChainInfo {
        var pathPattern = "/**"
        var pathType = PathType.GLOB
        var methods = ""
        var decoratorLabel = defaultDecoratorLabel
        for (step in steps) {
            when (step.methodName) {
                "path", "pathPrefix", "pathRegex", "pathGlob" -> {
                    val raw = step.firstStringArg ?: pathPattern
                    val parsed = ArmeriaRouteSupport.parsePathType(
                        when (step.methodName) {
                            "pathPrefix" -> "prefix:$raw"
                            "pathRegex" -> "regex:$raw"
                            "pathGlob" -> "glob:$raw"
                            else -> raw
                        },
                    )
                    pathType = parsed.first
                    pathPattern = parsed.second
                }
                "methods", "method" -> {
                    methods = step.rawMethodArgs.joinToString(", ") { argument ->
                        argument.removePrefix("HttpMethod.").uppercase()
                    }
                }
                "build" -> {
                    val decoratorArg = step.rawMethodArgs.firstOrNull()
                    if (decoratorArg != null) {
                        decoratorLabel = ArmeriaDecoratorSupport.labelDecorator(decoratorArg)
                    }
                }
            }
        }
        return RouteDecoratorChainInfo(pathPattern, pathType, methods, decoratorLabel)
    }

    private fun parsePathFromStep(step: RegistrationChainStep): Pair<PathType, String>? {
        val raw = step.firstStringArg ?: return null
        return ArmeriaRouteSupport.parsePathType(raw)
    }
}
