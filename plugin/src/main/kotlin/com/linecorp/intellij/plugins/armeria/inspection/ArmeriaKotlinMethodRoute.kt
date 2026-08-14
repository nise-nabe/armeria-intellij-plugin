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
        fun from(function: KtNamedFunction): ArmeriaKotlinMethodRoute? {
            val methodAnnotation =
                function.annotationEntries.firstNotNullOfOrNull { entry ->
                    val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: return@firstNotNullOfOrNull null
                    val method = ArmeriaRouteSupport.routeAnnotations[qualifiedName] ?: return@firstNotNullOfOrNull null
                    entry to method
                } ?: return null
            val classPrefix =
                PsiTreeUtil
                    .getParentOfType(function, KtClassOrObject::class.java)
                    ?.annotationEntries
                    ?.firstOrNull {
                        ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION
                    }?.let(::extractPathPrefix)
                    .orEmpty()
            val rawPaths =
                buildList {
                    addAll(extractPaths(methodAnnotation.first))
                    function.annotationEntries
                        .filter { ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_ANNOTATION }
                        .forEach { addAll(extractPaths(it)) }
                }.ifEmpty { listOf("/") }
            val paths =
                rawPaths
                    .map { rawPath -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, rawPath) }
                    .distinct()
            return ArmeriaKotlinMethodRoute(methodAnnotation.second, paths, rawPaths, classPrefix)
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
