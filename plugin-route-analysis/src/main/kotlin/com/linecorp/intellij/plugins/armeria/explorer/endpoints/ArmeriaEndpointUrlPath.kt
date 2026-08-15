package com.linecorp.intellij.plugins.armeria.explorer.endpoints

import com.intellij.microservices.url.UrlPath
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

internal object ArmeriaEndpointUrlPath {
    fun toUrlPath(
        path: String,
        pathType: PathType,
    ): UrlPath {
        if (path.isBlank() || path == "/") {
            return UrlPath.EMPTY
        }
        val normalized = if (path.startsWith("/")) path else "/$path"
        if (pathType == PathType.REGEX || pathType == PathType.GLOB) {
            return UrlPath.fromExactString(normalized)
        }
        return UrlPath(normalized.split('/').map { toSegment(it) })
    }

    private fun toSegment(segment: String): UrlPath.PathSegment {
        if (segment.startsWith(':') && segment.length > 1 && '{' !in segment) {
            return UrlPath.PathSegment.Variable(segment.substring(1))
        }
        if (segment.startsWith('{') && segment.endsWith('}') && segment.length > 2) {
            val inner = segment.substring(1, segment.lastIndex).trim()
            val colonIndex = inner.indexOf(':')
            val name = if (colonIndex < 0) inner else inner.substring(0, colonIndex).trim()
            val regex =
                if (colonIndex < 0) {
                    null
                } else {
                    inner.substring(colonIndex + 1).trim().ifEmpty { null }
                }
            return UrlPath.PathSegment.Variable(name, regex)
        }
        return UrlPath.PathSegment.Exact(segment)
    }
}
