package com.linecorp.intellij.plugins.armeria.explorer.endpoints

import com.intellij.microservices.url.Authority
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import java.util.Locale

internal object ArmeriaEndpointsSupport {
    private val GRPC_METHOD_PATH = Regex("""^/[^/]+/[^/]+$""")
    private val HTTP_SCHEMES = listOf("http", "https")

    fun isVisibleServerRoute(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.SERVICE,
            RouteMatch.SERVICE_UNDER,
            RouteMatch.HEALTH_CHECK,
            RouteMatch.ROUTE_FLUENT,
            -> true
            RouteMatch.CONFIG -> route.httpMethod.isNotBlank()
            RouteMatch.NON_HTTP -> isGrpcMethodRoute(route)
            RouteMatch.DELEGATED,
            RouteMatch.RUNTIME,
            RouteMatch.ANNOTATED_SERVICE,
            RouteMatch.FILE_SERVICE,
            RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR,
            RouteMatch.DECORATOR_UNDER,
            -> false
        }

    fun httpMethods(route: ArmeriaRoute): List<String> {
        if (isGrpcMethodRoute(route)) {
            return listOf("POST")
        }
        return route.httpMethod
            .split(',')
            .map { it.trim().substringAfterLast('.') }
            .filter { it.isNotEmpty() }
            .map { it.uppercase(Locale.ROOT) }
    }

    fun groupKey(route: ArmeriaRoute): String {
        val element = route.pointer.element
        val psiClass = element as? PsiClass ?: (element as? PsiMember)?.containingClass
        val className = psiClass?.qualifiedName
        val module = route.moduleName
        if (!className.isNullOrBlank()) {
            return "module:$module|class:$className"
        }
        val fileUrl =
            route.pointer.virtualFile?.url
                ?: element
                    ?.containingFile
                    ?.virtualFile
                    ?.url
        if (!fileUrl.isNullOrBlank()) {
            return "module:$module|file:$fileUrl"
        }
        return "module:$module"
    }

    fun urlTargetInfo(route: ArmeriaRoute): UrlTargetInfo = ArmeriaUrlTargetInfo(route)

    private fun isGrpcMethodRoute(route: ArmeriaRoute): Boolean {
        if (route.routeMatch != RouteMatch.NON_HTTP) {
            return false
        }
        if (!route.protocol.equals(RouteProtocol.GRPC.presentableName(), ignoreCase = true)) {
            return false
        }
        return GRPC_METHOD_PATH.matches(route.path)
    }

    private class ArmeriaUrlTargetInfo(
        private val route: ArmeriaRoute,
    ) : UrlTargetInfo {
        override val schemes: List<String> = HTTP_SCHEMES

        override val authorities: List<Authority> =
            if (route.virtualHostName.isBlank()) {
                emptyList()
            } else {
                listOf(Authority.Exact(route.virtualHostName))
            }

        override val path: UrlPath =
            ArmeriaEndpointUrlPath.toUrlPath(route.path, route.pathType, route.routeMatch)

        override val methods: Set<String> = httpMethods(route).toSet()

        override val source: String = route.shortTarget

        override fun resolveToPsiElement(): PsiElement? = route.pointer.element
    }
}
