package com.linecorp.intellij.plugins.armeria.explorer.collector.annotation
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaAnnotatedMetadataSupport {
    private const val MATCHES_HEADER_ANNOTATION = ArmeriaRouteSupport.MATCHES_HEADER_ANNOTATION
    private const val STATUS_CODE_ANNOTATION = "com.linecorp.armeria.server.annotation.StatusCode"
    private const val CONSUMES_ANNOTATION = "com.linecorp.armeria.server.annotation.Consumes"
    private const val PRODUCES_ANNOTATION = "com.linecorp.armeria.server.annotation.Produces"
    private const val CONSUMES_JSON_ANNOTATION = ArmeriaRouteSupport.CONSUMES_JSON_ANNOTATION
    private const val PRODUCES_JSON_ANNOTATION = ArmeriaRouteSupport.PRODUCES_JSON_ANNOTATION
    private const val DESCRIPTION_ANNOTATION = "com.linecorp.armeria.server.annotation.Description"
    private const val JSON_MEDIA_TYPE = "application/json"

    fun collectContentHints(
        method: PsiMethod,
        path: String,
        pathType: PathType,
    ): List<String> {
        val methodDescription = method.getAnnotation(DESCRIPTION_ANNOTATION)
        return buildList {
            addAll(collectHeaderMatches(method))
            collectStatusCode(method)?.let { add(it) }
            collectMediaTypes(method, CONSUMES_ANNOTATION, CONSUMES_JSON_ANNOTATION, "route.explorer.hint.consumes")
                ?.let { add(it) }
            collectMediaTypes(method, PRODUCES_ANNOTATION, PRODUCES_JSON_ANNOTATION, "route.explorer.hint.produces")
                ?.let { add(it) }
            collectDescription(methodDescription)?.let { add(it) }
            collectClassDescription(method.containingClass, methodDescription)?.let { add(it) }
            collectPathVariables(path, pathType).takeIf { it.isNotEmpty() }?.let { vars ->
                add(message("route.explorer.hint.pathVariables", vars.joinToString(", ")))
            }
        }
    }

    private fun collectHeaderMatches(method: PsiMethod): List<String> =
        method.annotations
            .filter { it.qualifiedName == MATCHES_HEADER_ANNOTATION }
            .flatMap { ArmeriaRouteSupport.extractStrings(it.findDeclaredAttributeValue("value")) }
            .map { header -> message("route.explorer.hint.matchesHeader", header) }

    private fun collectStatusCode(method: PsiMethod): String? {
        val annotation = method.getAnnotation(STATUS_CODE_ANNOTATION) ?: return null
        val valueAttribute = annotation.findDeclaredAttributeValue("value") ?: return null
        val value =
            JavaPsiFacade
                .getInstance(method.project)
                .constantEvaluationHelper
                .computeConstantExpression(valueAttribute)
                ?.toString()
                ?: valueAttribute.text.trim()
        return message("route.explorer.hint.statusCode", value)
    }

    private fun collectMediaTypes(
        method: PsiMethod,
        annotationFqn: String,
        jsonHelperFqn: String,
        messageKey: String,
    ): String? {
        val types =
            (
                mediaTypesOn(method.annotations, method.getAnnotation(jsonHelperFqn) != null, annotationFqn) +
                    mediaTypesOn(
                        method.containingClass?.annotations.orEmpty(),
                        method.containingClass?.getAnnotation(jsonHelperFqn) != null,
                        annotationFqn,
                    )
            ).distinct()
        if (types.isEmpty()) {
            return null
        }
        return message(messageKey, types.joinToString(", "))
    }

    private fun mediaTypesOn(
        annotations: Array<out PsiAnnotation>,
        jsonHelperPresent: Boolean,
        annotationFqn: String,
    ): List<String> =
        buildList {
            addAll(
                annotations
                    .filter { it.qualifiedName == annotationFqn }
                    .flatMap { annotation ->
                        ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value"))
                    },
            )
            if (jsonHelperPresent) {
                add(JSON_MEDIA_TYPE)
            }
        }

    private fun collectDescription(annotation: PsiAnnotation?): String? =
        descriptionText(annotation)?.let {
            message("route.explorer.hint.description", it)
        }

    private fun collectClassDescription(
        containingClass: PsiClass?,
        methodDescription: PsiAnnotation?,
    ): String? {
        val classAnnotation = containingClass?.getAnnotation(DESCRIPTION_ANNOTATION) ?: return null
        val classText = descriptionText(classAnnotation) ?: return null
        if (classText == descriptionText(methodDescription)) {
            return null
        }
        return message("route.explorer.hint.description", classText)
    }

    private fun descriptionText(annotation: PsiAnnotation?): String? {
        if (annotation == null) {
            return null
        }
        return firstStringOrRawText(annotation)?.takeIf { it.isNotBlank() }
    }

    private fun firstStringOrRawText(
        annotation: PsiAnnotation,
        attribute: String = "value",
    ): String? {
        val value = annotation.findDeclaredAttributeValue(attribute) ?: return null
        return ArmeriaRouteSupport.extractStrings(value).firstOrNull() ?: value.text.trim('"')
    }

    private fun collectPathVariables(
        path: String,
        pathType: PathType,
    ): List<String> = ArmeriaPathVariableSupport.extractPathVariables(path, pathType)
}
