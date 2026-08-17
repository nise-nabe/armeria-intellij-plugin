package com.linecorp.intellij.plugins.armeria.explorer.collector.annotation

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaAnnotatedMetadataSupport {
    private const val MATCHES_HEADER_ANNOTATION = ArmeriaRouteSupport.MATCHES_HEADER_ANNOTATION
    private const val MATCHES_PARAM_ANNOTATION = ArmeriaRouteSupport.MATCHES_PARAM_ANNOTATION
    private const val STATUS_CODE_ANNOTATION = "com.linecorp.armeria.server.annotation.StatusCode"
    private const val CONSUMES_ANNOTATION = ArmeriaRouteSupport.CONSUMES_ANNOTATION
    private const val PRODUCES_ANNOTATION = ArmeriaRouteSupport.PRODUCES_ANNOTATION
    private const val DESCRIPTION_ANNOTATION = ArmeriaRouteSupport.DESCRIPTION_ANNOTATION
    private const val JSON_MEDIA_TYPE = "application/json"
    private const val PLAIN_TEXT_MEDIA_TYPE = "text/plain"
    private const val BINARY_MEDIA_TYPE = "application/binary"

    private val CONSUMES_HELPERS =
        mapOf(
            ArmeriaRouteSupport.CONSUMES_JSON_ANNOTATION to JSON_MEDIA_TYPE,
        )
    private val PRODUCES_HELPERS =
        mapOf(
            ArmeriaRouteSupport.PRODUCES_JSON_ANNOTATION to JSON_MEDIA_TYPE,
            ArmeriaRouteSupport.PRODUCES_TEXT_ANNOTATION to PLAIN_TEXT_MEDIA_TYPE,
            ArmeriaRouteSupport.PRODUCES_BINARY_ANNOTATION to BINARY_MEDIA_TYPE,
        )
    private val BINDING_NAME_ANNOTATIONS =
        listOf(
            ArmeriaRouteSupport.PARAM_ANNOTATION,
            ArmeriaRouteSupport.HEADER_ANNOTATION,
            ArmeriaRouteSupport.ATTRIBUTE_ANNOTATION,
            ArmeriaRouteSupport.COOKIE_ANNOTATION,
        )

    fun collectContentHints(
        method: PsiMethod,
        path: String,
        pathType: PathType,
    ): List<String> {
        val methodDescription = method.getAnnotation(DESCRIPTION_ANNOTATION)
        return buildList {
            addAll(collectHeaderMatches(method))
            addAll(collectParamMatches(method))
            collectStatusCode(method)?.let { add(it) }
            collectMediaTypes(method, CONSUMES_ANNOTATION, CONSUMES_HELPERS, "route.explorer.hint.consumes")
                ?.let { add(it) }
            collectMediaTypes(method, PRODUCES_ANNOTATION, PRODUCES_HELPERS, "route.explorer.hint.produces")
                ?.let { add(it) }
            addAll(collectDefaults(method))
            collectDescription(methodDescription)?.let { add(it) }
            collectClassDescription(method.containingClass, methodDescription)?.let { add(it) }
            collectPathVariables(path, pathType).takeIf { it.isNotEmpty() }?.let { vars ->
                add(message("route.explorer.hint.pathVariables", vars.joinToString(", ")))
            }
        }
    }

    private fun collectHeaderMatches(method: PsiMethod): List<String> =
        collectMatches(method, MATCHES_HEADER_ANNOTATION, "route.explorer.hint.matchesHeader")

    private fun collectParamMatches(method: PsiMethod): List<String> =
        collectMatches(method, MATCHES_PARAM_ANNOTATION, "route.explorer.hint.matchesParam")

    private fun collectMatches(
        method: PsiMethod,
        annotationFqn: String,
        messageKey: String,
    ): List<String> {
        val annotations = method.annotations.toList() + method.containingClass?.annotations.orEmpty()
        return annotations
            .filter { it.qualifiedName == annotationFqn }
            .flatMap { ArmeriaRouteSupport.extractStrings(it.findDeclaredAttributeValue("value")) }
            .distinct()
            .map { value -> message(messageKey, value) }
    }

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
        helpers: Map<String, String>,
        messageKey: String,
    ): String? {
        val types =
            (
                mediaTypesOn(method.annotations, annotationFqn, helpers) +
                    mediaTypesOn(method.containingClass?.annotations.orEmpty(), annotationFqn, helpers)
            ).distinct()
        if (types.isEmpty()) {
            return null
        }
        return message(messageKey, types.joinToString(", "))
    }

    private fun mediaTypesOn(
        annotations: Array<out PsiAnnotation>,
        annotationFqn: String,
        helpers: Map<String, String>,
    ): List<String> =
        buildList {
            addAll(
                annotations
                    .filter { it.qualifiedName == annotationFqn }
                    .flatMap { annotation ->
                        ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value"))
                    },
            )
            annotations.forEach { annotation ->
                helpers[annotation.qualifiedName]?.let { add(it) }
            }
        }

    private fun collectDefaults(method: PsiMethod): List<String> =
        method.parameterList.parameters.mapNotNull { parameter ->
            val annotation = parameter.getAnnotation(ArmeriaRouteSupport.DEFAULT_ANNOTATION) ?: return@mapNotNull null
            val value = firstStringOrRawText(annotation)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = parameterBindingName(parameter) ?: return@mapNotNull null
            message("route.explorer.hint.default", "$name=$value")
        }

    private fun parameterBindingName(parameter: PsiParameter): String? {
        val named =
            BINDING_NAME_ANNOTATIONS.firstNotNullOfOrNull { fqn ->
                val annotation = parameter.getAnnotation(fqn) ?: return@firstNotNullOfOrNull null
                ArmeriaRouteSupport
                    .extractStrings(annotation.findDeclaredAttributeValue("value"))
                    .firstOrNull { it.isNotBlank() }
            }
        return named ?: parameter.name?.takeIf { it.isNotBlank() }
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
