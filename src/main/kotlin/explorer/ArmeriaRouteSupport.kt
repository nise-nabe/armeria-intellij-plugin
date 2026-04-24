package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationArrayInitializerMemberValue
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod

object ArmeriaRouteSupport {
    const val pathPrefixAnnotation = "com.linecorp.armeria.server.annotation.PathPrefix"
    const val decoratorAnnotation = "com.linecorp.armeria.server.annotation.Decorator"
    const val exceptionHandlerAnnotation = "com.linecorp.armeria.server.annotation.ExceptionHandler"

    val routeAnnotations = mapOf(
        "com.linecorp.armeria.server.annotation.Get" to "GET",
        "com.linecorp.armeria.server.annotation.Head" to "HEAD",
        "com.linecorp.armeria.server.annotation.Post" to "POST",
        "com.linecorp.armeria.server.annotation.Put" to "PUT",
        "com.linecorp.armeria.server.annotation.Delete" to "DELETE",
        "com.linecorp.armeria.server.annotation.Options" to "OPTIONS",
        "com.linecorp.armeria.server.annotation.Patch" to "PATCH",
        "com.linecorp.armeria.server.annotation.Trace" to "TRACE",
    )

    fun findRouteAnnotation(method: PsiMethod): Pair<PsiAnnotation, String>? {
        return method.modifierList.annotations.firstNotNullOfOrNull { candidate ->
            val qualifiedName = candidate.qualifiedName ?: return@firstNotNullOfOrNull null
            routeAnnotations[qualifiedName]?.let { candidate to it }
        }
    }

    fun extractPaths(annotation: PsiAnnotation): List<String> {
        val values = extractStrings(annotation.findDeclaredAttributeValue("value"))
        if (values.isNotEmpty()) {
            return values.map(::normalizePath)
        }
        val pathValues = extractStrings(annotation.findDeclaredAttributeValue("path"))
        if (pathValues.isNotEmpty()) {
            return pathValues.map(::normalizePath)
        }
        return emptyList()
    }

    fun extractPrimaryPath(annotation: PsiAnnotation?): String {
        if (annotation == null) {
            return ""
        }
        return extractPaths(annotation).firstOrNull().orEmpty()
    }

    fun extractNames(annotation: PsiAnnotation?): List<String> {
        if (annotation == null) {
            return emptyList()
        }
        val value = annotation.findDeclaredAttributeValue("value")
        return extractStrings(value)
            .ifEmpty { listOf(renderMemberValue(value)) }
            .mapNotNull { it.takeIf(String::isNotBlank) }
    }

    fun extractStrings(value: PsiAnnotationMemberValue?): List<String> {
        return when (value) {
            null -> emptyList()
            is PsiLiteralExpression -> listOfNotNull(value.value as? String)
            is PsiAnnotationArrayInitializerMemberValue -> value.initializers.flatMap(::extractStrings)
            else -> listOf(renderMemberValue(value)).filter(String::isNotBlank)
        }
    }

    fun renderMemberValue(value: PsiAnnotationMemberValue?): String {
        return value?.text?.removePrefix("\"")?.removeSuffix("\"").orEmpty()
    }

    fun combinePaths(prefix: String, path: String): String {
        val normalizedPrefix = normalizePath(prefix)
        val normalizedPath = normalizePath(path)
        if (normalizedPrefix == "/") {
            return normalizedPath
        }
        if (normalizedPath == "/") {
            return normalizedPrefix
        }
        return "${normalizedPrefix.removeSuffix("/")}/${normalizedPath.removePrefix("/")}"
    }

    fun normalizePath(path: String): String {
        if (path.isBlank()) {
            return "/"
        }
        val candidate = path.trim()
        return if (candidate.startsWith("/")) candidate else "/$candidate"
    }
}
