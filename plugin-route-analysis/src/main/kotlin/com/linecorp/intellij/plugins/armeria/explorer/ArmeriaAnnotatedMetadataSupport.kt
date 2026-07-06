package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaAnnotatedMetadataSupport {
    private const val MATCHES_HEADER_ANNOTATION = "com.linecorp.armeria.server.annotation.MatchesHeader"
    private const val STATUS_CODE_ANNOTATION = "com.linecorp.armeria.server.annotation.StatusCode"
    private const val CONSUMES_ANNOTATION = "com.linecorp.armeria.server.annotation.Consumes"
    private const val PRODUCES_ANNOTATION = "com.linecorp.armeria.server.annotation.Produces"
    private const val REDIRECT_ANNOTATION = "com.linecorp.armeria.server.annotation.Redirect"
    private const val DESCRIPTION_ANNOTATION = "com.linecorp.armeria.server.annotation.Description"

    fun collectContentHints(method: PsiMethod, path: String): List<String> {
        return buildList {
            addAll(collectHeaderMatches(method))
            collectStatusCode(method)?.let { add(it) }
            collectMediaTypes(method, CONSUMES_ANNOTATION, "route.explorer.hint.consumes")?.let { add(it) }
            collectMediaTypes(method, PRODUCES_ANNOTATION, "route.explorer.hint.produces")?.let { add(it) }
            collectRedirect(method)?.let { add(it) }
            collectDescription(method)?.let { add(it) }
            collectClassDescription(method.containingClass)?.let { add(it) }
            collectPathVariables(path).takeIf { it.isNotEmpty() }?.let { vars ->
                add(message("route.explorer.hint.pathVariables", vars.joinToString(", ")))
            }
        }
    }

    private fun collectHeaderMatches(method: PsiMethod): List<String> {
        return method.annotations
            .filter { it.qualifiedName == MATCHES_HEADER_ANNOTATION }
            .flatMap { ArmeriaRouteSupport.extractStrings(it.findDeclaredAttributeValue("value")) }
            .map { header -> message("route.explorer.hint.matchesHeader", header) }
    }

    private fun collectStatusCode(method: PsiMethod): String? {
        val annotation = method.getAnnotation(STATUS_CODE_ANNOTATION) ?: return null
        val valueAttribute = annotation.findDeclaredAttributeValue("value") ?: return null
        val value = JavaPsiFacade.getInstance(method.project)
            .constantEvaluationHelper
            .computeConstantExpression(valueAttribute)
            ?.toString()
            ?: valueAttribute.text.trim()
        return message("route.explorer.hint.statusCode", value)
    }

    private fun collectMediaTypes(method: PsiMethod, annotationFqn: String, messageKey: String): String? {
        val annotation = method.annotations.firstOrNull { it.qualifiedName == annotationFqn } ?: return null
        val types = ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value"))
            .ifEmpty { ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("types")) }
        if (types.isEmpty()) {
            return null
        }
        return message(messageKey, types.joinToString(", "))
    }

    private fun collectRedirect(method: PsiMethod): String? {
        val annotation = method.annotations.firstOrNull { it.qualifiedName == REDIRECT_ANNOTATION } ?: return null
        val target = ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value")).firstOrNull()
            ?: annotation.findDeclaredAttributeValue("value")?.text?.trim('"')
        return target?.let { message("route.explorer.hint.redirect", it) }
    }

    private fun collectDescription(method: PsiMethod): String? {
        return collectDescriptionText(method.getAnnotation(DESCRIPTION_ANNOTATION))
    }

    private fun collectClassDescription(containingClass: PsiClass?): String? {
        return collectDescriptionText(containingClass?.getAnnotation(DESCRIPTION_ANNOTATION))
    }

    private fun collectDescriptionText(annotation: PsiAnnotation?): String? {
        if (annotation == null) {
            return null
        }
        val text = ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value")).firstOrNull()
            ?: annotation.findDeclaredAttributeValue("value")?.text?.trim('"')
        return text?.takeIf { it.isNotBlank() }?.let { message("route.explorer.hint.description", it) }
    }

    private fun collectPathVariables(path: String): List<String> {
        val variables = linkedSetOf<String>()
        BRACE_PATH_VARIABLE_PATTERN.findAll(path).forEach { variables += it.groupValues[1] }
        COLON_PATH_VARIABLE_PATTERN.findAll(path).forEach { variables += it.groupValues[1] }
        return variables.toList()
    }

    private val BRACE_PATH_VARIABLE_PATTERN = Regex("""\{([^}]+)}""")
    private val COLON_PATH_VARIABLE_PATTERN = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
}
