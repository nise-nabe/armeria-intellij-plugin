package com.linecorp.intellij.plugins.armeria.explorer.collector.registration
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ParsedArmeriaPath

internal data class RegistrationChainStep(
    val methodName: String,
    val firstStringArg: String?,
    val rawMethodArgs: List<String>,
    val secondStringArg: String? = null,
)

internal data class FluentRouteChainInfo(
    val httpMethod: String,
    val path: String,
    val pathType: PathType,
    val target: String,
    val explicitExactPath: Boolean = false,
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
        var explicitExactPath = false
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
                    if (step.hasUnresolvedPathArg()) {
                        // An unresolved path argument must not collapse the route to "/".
                        return null
                    }
                    parsePathFromStep(step)?.let { parsed ->
                        methodPath = parsed.path
                        path = parsed.path
                        pathType = parsed.pathType
                        explicitExactPath = parsed.explicitExactPath
                        foundPath = true
                    }
                }

                "path" -> {
                    if (step.hasUnresolvedPathArg()) {
                        return null
                    }
                    if (step.rawMethodArgs.size >= 2 && step.secondStringArg == null) {
                        // RouteBuilder.path(prefix, pathPattern) — an unresolvable
                        // pattern argument means the route is unknown.
                        return null
                    }
                    val parsed =
                        if (step.rawMethodArgs.size >= 2) {
                            ArmeriaRouteSupport.parsePathWithPrefix(
                                step.firstStringArg ?: "/",
                                step.secondStringArg ?: "",
                            )
                        } else {
                            parsePathFromStep(step)
                        }
                    parsed?.let {
                        methodPath = it.path
                        path = it.path
                        pathType = it.pathType
                        explicitExactPath = it.explicitExactPath
                        foundPath = true
                    }
                }

                "pathPrefix" -> {
                    if (step.hasUnresolvedPathArg()) {
                        return null
                    }
                    val raw = step.firstStringArg ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("prefix:$raw")
                    mountPrefix = parsed.path
                    pathType = parsed.pathType
                    path = parsed.path
                    foundPath = true
                }

                "pathRegex" -> {
                    if (step.hasUnresolvedPathArg()) {
                        return null
                    }
                    val raw = step.firstStringArg ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("regex:$raw")
                    pathType = parsed.pathType
                    path = parsed.path
                    foundPath = true
                }

                "pathGlob" -> {
                    if (step.hasUnresolvedPathArg()) {
                        return null
                    }
                    val raw = step.firstStringArg ?: path
                    val parsed = ArmeriaRouteSupport.parsePathType("glob:$raw")
                    pathType = parsed.pathType
                    path = parsed.path
                    foundPath = true
                }

                "methods", "method" -> {
                    httpMethod =
                        step.rawMethodArgs.joinToString(", ") { argument ->
                            formatHttpMethodArgument(argument)
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
        val resolvedPath =
            if (mountPrefix != null && methodPath != null) {
                ArmeriaRouteSupport.combinePaths(mountPrefix, methodPath)
            } else {
                path
            }
        val resolvedPathType =
            if (mountPrefix != null && methodPath != null) {
                PathType.EXACT
            } else {
                pathType
            }
        return FluentRouteChainInfo(
            httpMethod = httpMethod,
            path = resolvedPath,
            pathType = resolvedPathType,
            target = handlerTarget ?: defaultTarget,
            explicitExactPath = explicitExactPath,
        )
    }

    fun reduceRouteDecoratorChain(
        steps: List<RegistrationChainStep>,
        defaultDecoratorLabel: String,
    ): RouteDecoratorChainInfo? {
        var pathPattern = "/**"
        var pathType = PathType.GLOB
        var methods = ""
        var decoratorLabel = defaultDecoratorLabel
        for (step in steps) {
            when (step.methodName) {
                "path", "pathPrefix", "pathRegex", "pathGlob" -> {
                    if (step.hasUnresolvedPathArg()) {
                        // The decorator scope is unknown — must not fabricate "/**".
                        return null
                    }
                    val raw = step.firstStringArg ?: pathPattern
                    val parsed =
                        ArmeriaRouteSupport.parsePathType(
                            when (step.methodName) {
                                "pathPrefix" -> "prefix:$raw"
                                "pathRegex" -> "regex:$raw"
                                "pathGlob" -> "glob:$raw"
                                else -> raw
                            },
                        )
                    pathType = parsed.pathType
                    pathPattern = parsed.path
                }

                "methods", "method" -> {
                    methods =
                        step.rawMethodArgs.joinToString(", ") { argument ->
                            formatHttpMethodArgument(argument)
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

    private fun parsePathFromStep(step: RegistrationChainStep): ParsedArmeriaPath? {
        val raw = step.firstStringArg ?: return null
        return ArmeriaRouteSupport.parsePathType(raw)
    }

    /**
     * True when the step carries arguments that did not resolve to a constant —
     * distinct from a no-argument call, which is a legitimate default.
     */
    private fun RegistrationChainStep.hasUnresolvedPathArg(): Boolean = firstStringArg == null && rawMethodArgs.isNotEmpty()

    private fun formatHttpMethodArgument(argument: String): String = argument.trim().substringAfterLast('.').uppercase()
}
