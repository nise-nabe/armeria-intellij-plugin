package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

internal data class ArmeriaKotlinMethodRoute(
    val httpMethod: String,
    val paths: List<String>,
) {
    companion object {
        fun from(function: KtNamedFunction): ArmeriaKotlinMethodRoute? {
            val methodAnnotation = function.annotationEntries.firstNotNullOfOrNull { entry ->
                val qualifiedName = entry.qualifiedName() ?: return@firstNotNullOfOrNull null
                val method = ArmeriaRouteSupport.routeAnnotations[qualifiedName] ?: return@firstNotNullOfOrNull null
                entry to method
            } ?: return null
            val classPrefix = PsiTreeUtil.getParentOfType(function, KtClassOrObject::class.java)?.annotationEntries
                ?.firstOrNull { it.qualifiedName() == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION }
                ?.let(::extractPathPrefix)
                .orEmpty()
            val paths = buildList {
                addAll(extractPaths(methodAnnotation.first))
                function.annotationEntries
                    .filter { it.qualifiedName() == ArmeriaRouteSupport.PATH_ANNOTATION }
                    .forEach { addAll(extractPaths(it)) }
            }.ifEmpty { listOf("/") }
                .map { rawPath -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, rawPath) }
                .distinct()
            return ArmeriaKotlinMethodRoute(methodAnnotation.second, paths)
        }

        private fun KtAnnotationEntry.qualifiedName(): String? {
            resolveAnnotationType()?.let { return it }
            val shortName = shortName?.asString() ?: return null
            containingKtFile.importDirectives
                .mapNotNull { it.importPath?.pathStr }
                .firstOrNull { it == shortName || it.endsWith(".$shortName") }
                ?.let { return it }
            return containingKtFile.declarations
                .filterIsInstance<KtClass>()
                .firstOrNull { it.name == shortName }
                ?.fqName?.asString()
        }

        private fun KtAnnotationEntry.resolveAnnotationType(): String? {
            val candidates = listOfNotNull(
                typeReference?.references?.firstOrNull()?.resolve(),
                calleeExpression?.references?.firstOrNull()?.resolve(),
            )
            for (resolved in candidates) {
                when (resolved) {
                    is PsiClass -> resolved.qualifiedName?.let { return it }
                    is KtClass -> resolved.fqName?.asString()?.let { return it }
                }
            }
            return null
        }

        private fun extractPathPrefix(annotation: KtAnnotationEntry): String {
            return extractPaths(annotation).firstOrNull().orEmpty()
        }

        private fun extractPaths(annotation: KtAnnotationEntry): List<String> {
            val valuePaths = extractNamedKotlinPaths(annotation, "value")
            if (valuePaths.isNotEmpty()) {
                return valuePaths.map(::preserveOrNormalizePath)
            }
            val pathPaths = extractNamedKotlinPaths(annotation, "path")
            if (pathPaths.isNotEmpty()) {
                return pathPaths.map(::preserveOrNormalizePath)
            }
            return emptyList()
        }

        private fun extractNamedKotlinPaths(annotation: KtAnnotationEntry, name: String): List<String> {
            val named = annotation.valueArguments
                .filter { it.getArgumentName()?.asName?.asString() == name }
                .flatMap { argument ->
                    ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
                }
            if (named.isNotEmpty()) {
                return named
            }
            if (name != "value") {
                return emptyList()
            }
            return annotation.valueArguments
                .filter { it.getArgumentName() == null }
                .flatMap { argument ->
                    ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
                }
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
