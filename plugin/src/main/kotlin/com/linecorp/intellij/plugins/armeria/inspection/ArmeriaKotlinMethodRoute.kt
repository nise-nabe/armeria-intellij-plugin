package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

internal data class ArmeriaKotlinMethodRoute(
    val httpMethod: String,
    val paths: List<String>,
) {
    companion object {
        private val HTTP_METHOD_ANNOTATIONS = mapOf(
            "Get" to "GET",
            "Head" to "HEAD",
            "Post" to "POST",
            "Put" to "PUT",
            "Delete" to "DELETE",
            "Options" to "OPTIONS",
            "Patch" to "PATCH",
            "Trace" to "TRACE",
        )

        fun from(function: KtNamedFunction): ArmeriaKotlinMethodRoute? {
            val methodAnnotation = function.annotationEntries.firstNotNullOfOrNull { entry ->
                val method = HTTP_METHOD_ANNOTATIONS[entry.shortName?.asString()] ?: return@firstNotNullOfOrNull null
                entry to method
            } ?: return null
            val classPrefix = PsiTreeUtil.getParentOfType(function, KtClass::class.java)?.annotationEntries
                ?.firstOrNull { it.shortName?.asString() == "PathPrefix" }
                ?.let(::extractPathPrefix)
                .orEmpty()
            val paths = buildList {
                addAll(extractPaths(methodAnnotation.first))
                function.annotationEntries
                    .filter { it.shortName?.asString() == "Path" }
                    .forEach { addAll(extractPaths(it)) }
            }.ifEmpty { listOf("/") }
                .map { rawPath -> ArmeriaRouteSupport.combinePaths(classPrefix, ArmeriaRouteSupport.normalizePath(rawPath)) }
                .distinct()
            return ArmeriaKotlinMethodRoute(methodAnnotation.second, paths)
        }

        private fun extractPathPrefix(annotation: KtAnnotationEntry): String {
            return extractPaths(annotation).firstOrNull().orEmpty()
        }

        private fun extractPaths(annotation: KtAnnotationEntry): List<String> {
            val valueArguments = annotation.valueArguments
            if (valueArguments.isEmpty()) {
                return emptyList()
            }
            return valueArguments.flatMap { argument ->
                ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
            }
        }
    }
}
