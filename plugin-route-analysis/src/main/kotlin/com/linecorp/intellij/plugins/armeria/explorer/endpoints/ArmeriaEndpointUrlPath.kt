package com.linecorp.intellij.plugins.armeria.explorer.endpoints

import com.intellij.microservices.url.UrlPath
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

internal object ArmeriaEndpointUrlPath {
    fun toUrlPath(
        path: String,
        pathType: PathType,
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
    ): UrlPath {
        val isPrefix = isPrefixPath(pathType, routeMatch)
        if (path.isBlank() || path == "/") {
            return if (isPrefix) {
                UrlPath(listOf(UrlPath.PathSegment.Undefined))
            } else {
                UrlPath.EMPTY
            }
        }
        val normalized = if (path.startsWith("/")) path else "/$path"
        if (pathType == PathType.REGEX || pathType == PathType.GLOB) {
            return UrlPath.fromExactString(normalized)
        }
        val body = if (isPrefix) normalized.trimEnd('/') else normalized
        if (body.isEmpty()) {
            return UrlPath(listOf(UrlPath.PathSegment.Undefined))
        }
        val segments = body.split('/').map { toSegment(it) }
        return UrlPath(if (isPrefix) segments + UrlPath.PathSegment.Undefined else segments)
    }

    private fun isPrefixPath(
        pathType: PathType,
        routeMatch: RouteMatch,
    ): Boolean {
        if (pathType == PathType.REGEX || pathType == PathType.GLOB) {
            return false
        }
        return pathType == PathType.PREFIX || routeMatch == RouteMatch.SERVICE_UNDER
    }

    private fun toSegment(segment: String): UrlPath.PathSegment {
        if (segment.startsWith(':') && segment.length > 1 && '{' !in segment) {
            return UrlPath.PathSegment.Variable(segment.substring(1))
        }
        val braceEnd = findMatchingBrace(segment, 0)
        if (segment.startsWith('{') && braceEnd == segment.lastIndex && segment.length > 2) {
            val inner = segment.substring(1, braceEnd).trim()
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

    private fun findMatchingBrace(
        path: String,
        start: Int,
    ): Int {
        if (start >= path.length || path[start] != '{') {
            return -1
        }
        var depth = 0
        for (index in start until path.length) {
            when (path[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
    }
}
