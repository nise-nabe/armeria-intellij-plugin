package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

internal data class ArmeriaKotlinMethodRoute(
    val httpMethod: String,
    val paths: List<String>,
    val rawPaths: List<String>,
    val classPrefix: String,
) {
    companion object {
        fun from(function: KtNamedFunction): ArmeriaKotlinMethodRoute? = all(function).firstOrNull()

        /**
         * One route per HTTP-method annotation entry. Armeria binds a route for each
         * annotation, and a path declared on the annotation cannot be combined with
         * `@Path` (line/armeria#1870, line/armeria#2853), so the entry's own paths take
         * precedence and `@Path` values apply only when the entry declares none.
         */
        fun all(function: KtNamedFunction): List<ArmeriaKotlinMethodRoute> {
            val methodAnnotations =
                function.annotationEntries.mapNotNull { entry ->
                    val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: return@mapNotNull null
                    val method = ArmeriaRouteSupport.routeAnnotations[qualifiedName] ?: return@mapNotNull null
                    entry to method
                }
            if (methodAnnotations.isEmpty()) {
                return emptyList()
            }
            val classPrefix =
                PsiTreeUtil
                    .getParentOfType(function, KtClassOrObject::class.java)
                    ?.annotationEntries
                    ?.firstOrNull {
                        ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION
                    }?.let(::extractPathPrefix)
                    .orEmpty()
            val pathAnnotationPaths =
                function.annotationEntries
                    .filter { ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_ANNOTATION }
                    .flatMap(::extractPaths)
            return methodAnnotations.map { (entry, httpMethod) ->
                val rawPaths =
                    extractPaths(entry)
                        .ifEmpty { pathAnnotationPaths }
                        .ifEmpty { listOf("/") }
                val paths =
                    rawPaths
                        .map { rawPath -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, rawPath) }
                        .distinct()
                ArmeriaKotlinMethodRoute(httpMethod, paths, rawPaths, classPrefix)
            }
        }

        private fun extractPathPrefix(annotation: KtAnnotationEntry): String = extractPaths(annotation).firstOrNull().orEmpty()

        private fun extractPaths(annotation: KtAnnotationEntry): List<String> {
            val valuePaths = ArmeriaKotlinAnnotationSupport.extractStrings(annotation, "value")
            if (valuePaths.isNotEmpty()) {
                return valuePaths.map(::preserveOrNormalizePath)
            }
            val pathPaths = ArmeriaKotlinAnnotationSupport.extractStrings(annotation, "path")
            if (pathPaths.isNotEmpty()) {
                return pathPaths.map(::preserveOrNormalizePath)
            }
            return emptyList()
        }

        private fun preserveOrNormalizePath(path: String): String {
            val trimmed = path.trim()
            return if (hasPathTypePrefix(trimmed)) trimmed else ArmeriaRouteSupport.normalizePath(trimmed)
        }

        private fun hasPathTypePrefix(path: String): Boolean =
            path.startsWith("prefix:") ||
                path.startsWith("regex:") ||
                path.startsWith("glob:") ||
                path.startsWith("exact:")
    }
}
