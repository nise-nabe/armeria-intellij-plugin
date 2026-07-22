package com.linecorp.intellij.plugins.armeria.explorer.model

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
    val contentHints: List<String> = emptyList(),
    val delegationMountPath: String = "",
    val delegationKind: DelegationKind? = null,
    val pointer: SmartPsiElementPointer<PsiElement>,
    /**
     * Optional text offset for language-agnostic / plain-text sources (e.g. Scala without PSI).
     * When set, Explorer navigation and [resolveSourceHint] prefer this offset over the pointer element range.
     */
    val sourceOffset: Int? = null,
) {
    fun resolveSourceHint(): String {
        val element = pointer.element ?: return ""
        return ArmeriaRouteMetadata.sourceHintAtOffset(element, sourceOffset ?: element.textRange.startOffset)
    }

    fun resolveRegisteredInHint(): String {
        val element = pointer.element ?: return ""
        return ArmeriaRouteMetadata.registeredInHint(element)
    }

    val methodLabel: String
        get() =
            when (routeMatch) {
                RouteMatch.ANNOTATED_HTTP -> httpMethod
                RouteMatch.ANNOTATED_SERVICE -> message("route.explorer.method.annotatedService")
                RouteMatch.SERVICE -> message("route.explorer.method.allHttp")
                RouteMatch.SERVICE_UNDER -> message("route.explorer.method.prefix")
                RouteMatch.FILE_SERVICE -> message("route.explorer.method.fileService")
                RouteMatch.HEALTH_CHECK -> message("route.explorer.method.healthCheck")
                RouteMatch.VIRTUAL_HOST -> message("route.explorer.method.virtualHost")
                RouteMatch.ROUTE_DECORATOR -> message("route.explorer.method.routeDecorator")
                RouteMatch.ROUTE_FLUENT -> httpMethod.ifBlank { message("route.explorer.method.allHttp") }
                RouteMatch.DECORATOR_UNDER -> message("route.explorer.method.decoratorUnder")
                RouteMatch.DELEGATED ->
                    httpMethod.ifBlank { message("route.explorer.method.allHttp") }
                RouteMatch.NON_HTTP -> protocol
                RouteMatch.RUNTIME, RouteMatch.CONFIG -> httpMethod
            }

    val shortTarget: String
        get() = truncateTarget(target)

    val speedSearchText: String
        get() =
            buildString {
                append(methodLabel)
                append(' ')
                append(path)
                append(' ')
                append(target)
                append(' ')
                append(moduleName)
            }

    val detailHandlerLabel: String
        get() =
            when {
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
            contentHints: List<String> = emptyList(),
            delegationMountPath: String = "",
            delegationKind: DelegationKind? = null,
            /** When set, overrides module attribution derived from [element] (e.g. concrete controller). */
            moduleName: String? = null,
            sourceOffset: Int? = null,
        ): ArmeriaRoute =
            ArmeriaRoute(
                protocol = protocol,
                httpMethod = httpMethod,
                path = path,
                target = target,
                routeMatch = routeMatch,
                moduleName = moduleName ?: ArmeriaRouteMetadata.moduleName(element),
                targetUnresolved = targetUnresolved,
                isDocService = isDocService,
                annotatedServiceHasPathPrefix = annotatedServiceHasPathPrefix,
                pathType = pathType,
                virtualHostName = virtualHostName,
                decorators = decorators,
                exceptionHandlers = exceptionHandlers,
                executionHints = executionHints,
                timeoutHints = timeoutHints,
                contentHints = contentHints,
                delegationMountPath = delegationMountPath,
                delegationKind = delegationKind,
                pointer = SmartPointerManager.createPointer(element),
                sourceOffset = sourceOffset,
            )

        fun createRuntime(
            httpMethod: String,
            path: String,
            target: String,
            moduleName: String,
            protocol: String,
            pointer: SmartPsiElementPointer<PsiElement>,
            delegationKind: DelegationKind? = null,
        ): ArmeriaRoute =
            ArmeriaRoute(
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
                delegationKind = delegationKind,
                pointer = pointer,
            )

        fun truncateTarget(
            value: String,
            limit: Int = TARGET_DISPLAY_LIMIT,
        ): String {
            if (value.length <= limit) {
                return value
            }
            return value.take(limit) + "…"
        }
    }
}
