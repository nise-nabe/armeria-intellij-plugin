package com.linecorp.intellij.plugins.armeria.client

import com.intellij.microservices.url.Authority
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointUrlPath
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

internal object ArmeriaClientEndpointsSupport {
    private val HTTP_SCHEMES = listOf("http", "https")

    fun isVisible(endpoint: ArmeriaClientEndpoint): Boolean = isVisibleHttpClientUri(endpoint.uri)

    fun isVisibleHttpClientUri(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        if (trimmed.startsWith("/")) {
            return true
        }
        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return true
        }
        val scheme = trimmed.substring(0, schemeSeparator)
        if (' ' in scheme || '(' in scheme) {
            return false
        }
        return ArmeriaClientRouteLinkSupport.isHttpLikeScheme(scheme)
    }

    fun groupKey(endpoint: ArmeriaClientEndpoint): String {
        val element = endpoint.pointer.element
        val psiClass = element as? PsiClass ?: (element as? PsiMember)?.containingClass
        val className = psiClass?.qualifiedName
        val module = endpoint.moduleName
        if (!className.isNullOrBlank()) {
            return "module:$module|class:$className"
        }
        val fileUrl =
            endpoint.sourceFileUrl
                ?: endpoint.pointer.virtualFile?.url
                ?: element
                    ?.containingFile
                    ?.virtualFile
                    ?.url
        if (!fileUrl.isNullOrBlank()) {
            return "module:$module|file:$fileUrl"
        }
        return "module:$module"
    }

    fun httpMethods(endpoint: ArmeriaClientEndpoint): List<String> {
        val protocol = ClientProtocol.fromPresentableName(endpoint.clientType) ?: return emptyList()
        return when (protocol) {
            ClientProtocol.GRPC,
            ClientProtocol.THRIFT,
            -> listOf("POST")
            ClientProtocol.HTTP,
            ClientProtocol.REST,
            ClientProtocol.BLOCKING,
            ClientProtocol.RETROFIT,
            -> emptyList()
        }
    }

    fun urlTargetInfo(endpoint: ArmeriaClientEndpoint): UrlTargetInfo = ArmeriaClientUrlTargetInfo(endpoint)

    private class ArmeriaClientUrlTargetInfo(
        private val endpoint: ArmeriaClientEndpoint,
    ) : UrlTargetInfo {
        private val uriParts = ArmeriaClientRouteLinkSupport.parseClientUri(endpoint.uri)

        override val schemes: List<String> = HTTP_SCHEMES

        override val authorities: List<Authority> =
            uriParts.host
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(Authority.Exact(it)) }
                .orEmpty()

        override val path: UrlPath =
            ArmeriaEndpointUrlPath.toUrlPath(uriParts.path, PathType.PREFIX)

        override val methods: Set<String> = httpMethods(endpoint).toSet()

        override val source: String = endpoint.target

        override fun resolveToPsiElement(): PsiElement? = endpoint.pointer.element
    }
}
