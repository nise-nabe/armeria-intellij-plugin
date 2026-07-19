package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

internal object ArmeriaRouteAnnotationSupport {
    const val PATH_ANNOTATION = "com.linecorp.armeria.server.annotation.Path"
    const val PATH_PREFIX_ANNOTATION = "com.linecorp.armeria.server.annotation.PathPrefix"
    const val DECORATOR_ANNOTATION = "com.linecorp.armeria.server.annotation.Decorator"
    const val EXCEPTION_HANDLER_ANNOTATION = "com.linecorp.armeria.server.annotation.ExceptionHandler"
    const val BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.Blocking"
    const val NON_BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.NonBlocking"

    const val GET_ANNOTATION = "com.linecorp.armeria.server.annotation.Get"
    const val HEAD_ANNOTATION = "com.linecorp.armeria.server.annotation.Head"
    const val POST_ANNOTATION = "com.linecorp.armeria.server.annotation.Post"
    const val PUT_ANNOTATION = "com.linecorp.armeria.server.annotation.Put"
    const val DELETE_ANNOTATION = "com.linecorp.armeria.server.annotation.Delete"
    const val OPTIONS_ANNOTATION = "com.linecorp.armeria.server.annotation.Options"
    const val PATCH_ANNOTATION = "com.linecorp.armeria.server.annotation.Patch"
    const val TRACE_ANNOTATION = "com.linecorp.armeria.server.annotation.Trace"

    val routeAnnotations =
        mapOf(
            GET_ANNOTATION to "GET",
            HEAD_ANNOTATION to "HEAD",
            POST_ANNOTATION to "POST",
            PUT_ANNOTATION to "PUT",
            DELETE_ANNOTATION to "DELETE",
            OPTIONS_ANNOTATION to "OPTIONS",
            PATCH_ANNOTATION to "PATCH",
            TRACE_ANNOTATION to "TRACE",
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
            return values.map(::preserveOrNormalizePath)
        }
        val pathValues = extractStrings(annotation.findDeclaredAttributeValue("path"))
        if (pathValues.isNotEmpty()) {
            return pathValues.map(::preserveOrNormalizePath)
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
        val names =
            when (value) {
                is PsiArrayInitializerMemberValue -> {
                    extractStrings(value).ifEmpty {
                        value.initializers.map(::renderMemberValue)
                    }
                }
                else -> extractStrings(value).ifEmpty { listOf(renderMemberValue(value)) }
            }
        return names.mapNotNull { it.takeIf(String::isNotBlank) }
    }

    fun extractStrings(value: PsiAnnotationMemberValue?): List<String> =
        when (value) {
            null -> emptyList()
            is PsiLiteralExpression -> listOfNotNull(value.value as? String)
            is PsiArrayInitializerMemberValue -> value.initializers.flatMap(::extractStrings)
            else -> evaluateConstant(value)?.let { listOf(it) } ?: emptyList()
        }

    fun renderMemberValue(value: PsiAnnotationMemberValue?): String =
        value
            ?.text
            ?.removePrefix("\"")
            ?.removeSuffix("\"")
            .orEmpty()

    fun extractPathAnnotations(method: PsiMethod): List<String> =
        method.annotations
            .filter { it.qualifiedName == PATH_ANNOTATION }
            .flatMap(::extractPaths)

    fun parsePathType(rawPath: String): Pair<PathType, String> {
        val trimmed = rawPath.trim()
        return when {
            trimmed.startsWith("prefix:") -> PathType.PREFIX to ArmeriaRouteSupport.normalizePath(trimmed.removePrefix("prefix:"))
            trimmed.startsWith("regex:") -> PathType.REGEX to trimmed.removePrefix("regex:").trim()
            trimmed.startsWith("glob:") -> PathType.GLOB to ArmeriaRouteSupport.normalizePath(trimmed.removePrefix("glob:"))
            trimmed.startsWith("exact:") -> PathType.EXACT to ArmeriaRouteSupport.normalizePath(trimmed.removePrefix("exact:"))
            else -> PathType.EXACT to ArmeriaRouteSupport.normalizePath(trimmed)
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

    private fun evaluateConstant(value: PsiAnnotationMemberValue): String? {
        val helper = JavaPsiFacade.getInstance(value.project).constantEvaluationHelper
        return helper.computeConstantExpression(value) as? String
    }
}
