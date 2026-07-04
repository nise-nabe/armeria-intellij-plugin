package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.message

data class ArmeriaRoute(
    val protocol: String,
    val httpMethod: String,
    val path: String,
    val target: String,
    val routeMatch: RouteMatch,
    val moduleName: String,
    val targetUnresolved: Boolean,
    val isDocService: Boolean,
    val annotatedServiceHasPathPrefix: Boolean = false,
    val pathType: PathType = PathType.EXACT,
    val virtualHostName: String = "",
    val decorators: List<String>,
    val exceptionHandlers: List<String>,
    val executionHints: List<String> = emptyList(),
    val timeoutHints: List<String> = emptyList(),
    val pointer: SmartPsiElementPointer<PsiElement>,
) {
    fun resolveSourceHint(): String {
        val element = pointer.element ?: return ""
        return ArmeriaRouteMetadata.sourceHint(element)
    }

    fun resolveRegisteredInHint(): String {
        val element = pointer.element ?: return ""
        return ArmeriaRouteMetadata.registeredInHint(element)
    }

    fun resolveRegistrationSummary(): String = ArmeriaRouteDetailFormatter.registrationSummary(this)

    val methodLabel: String
        get() = when (routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> httpMethod
            RouteMatch.ANNOTATED_SERVICE -> message("route.explorer.method.annotatedService")
            RouteMatch.SERVICE -> message("route.explorer.method.allHttp")
            RouteMatch.SERVICE_UNDER -> message("route.explorer.method.prefix")
            RouteMatch.FILE_SERVICE -> message("route.explorer.method.fileService")
            RouteMatch.HEALTH_CHECK -> message("route.explorer.method.healthCheck")
            RouteMatch.VIRTUAL_HOST -> message("route.explorer.method.virtualHost")
            RouteMatch.ROUTE_DECORATOR -> message("route.explorer.method.routeDecorator")
            RouteMatch.NON_HTTP -> protocol
            RouteMatch.RUNTIME -> httpMethod
        }

    val shortTarget: String
        get() = truncateTarget(target)

    val speedSearchText: String
        get() = buildString {
            append(methodLabel)
            append(' ')
            append(path)
            append(' ')
            append(target)
            append(' ')
            append(moduleName)
        }

    val detailHandlerLabel: String
        get() = when {
            routeMatch == RouteMatch.RUNTIME -> message("route.explorer.detail.service")
            targetUnresolved -> message("route.explorer.label.unresolvedExpression")
            else -> message("route.explorer.detail.handler")
        }

    companion object {
        private const val TARGET_DISPLAY_LIMIT = 60

        fun create(
            element: PsiElement,
            protocol: String,
            httpMethod: String,
            path: String,
            target: String,
            routeMatch: RouteMatch,
            targetUnresolved: Boolean = false,
            isDocService: Boolean = false,
            annotatedServiceHasPathPrefix: Boolean = false,
            pathType: PathType = PathType.EXACT,
            virtualHostName: String = "",
            decorators: List<String> = emptyList(),
            exceptionHandlers: List<String> = emptyList(),
            executionHints: List<String> = emptyList(),
            timeoutHints: List<String> = emptyList(),
        ): ArmeriaRoute {
            return ArmeriaRoute(
                protocol = protocol,
                httpMethod = httpMethod,
                path = path,
                target = target,
                routeMatch = routeMatch,
                moduleName = ArmeriaRouteMetadata.moduleName(element),
                targetUnresolved = targetUnresolved,
                isDocService = isDocService,
                annotatedServiceHasPathPrefix = annotatedServiceHasPathPrefix,
                pathType = pathType,
                virtualHostName = virtualHostName,
                decorators = decorators,
                exceptionHandlers = exceptionHandlers,
                executionHints = executionHints,
                timeoutHints = timeoutHints,
                pointer = SmartPointerManager.createPointer(element),
            )
        }

        fun createRuntime(
            httpMethod: String,
            path: String,
            target: String,
            moduleName: String,
            protocol: String,
            project: Project? = null,
        ): ArmeriaRoute {
            return ArmeriaRoute(
                protocol = protocol,
                httpMethod = httpMethod,
                path = path,
                target = target,
                routeMatch = RouteMatch.RUNTIME,
                moduleName = moduleName,
                targetUnresolved = false,
                isDocService = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                pointer = project?.let(::ArmeriaRuntimeRoutePointer) ?: ArmeriaRuntimeRoutePointer.withoutProject(),
            )
        }

        fun truncateTarget(value: String, limit: Int = TARGET_DISPLAY_LIMIT): String {
            if (value.length <= limit) {
                return value
            }
            return value.take(limit) + "…"
        }
    }
}
