package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod

internal enum class ServiceRegistrationMethod(val methodName: String) {
    SERVICE("service"),
    SERVICE_UNDER("serviceUnder"),
    ANNOTATED_SERVICE("annotatedService"),
    ;

    companion object {
        val METHOD_NAMES: Set<String> = entries.mapTo(linkedSetOf()) { it.methodName }

        fun fromMethodName(name: String): ServiceRegistrationMethod? =
            entries.firstOrNull { it.methodName == name }
    }
}

object ArmeriaRouteSupport {
    const val ARMERIA_PACKAGE_PREFIX = "com.linecorp.armeria"
    const val ARMERIA_SERVER_PACKAGE_PREFIX = "com.linecorp.armeria.server"
    const val SERVER_BUILDER_CLASS = "com.linecorp.armeria.server.ServerBuilder"
    const val SERVER_BUILDER_SIMPLE_NAME = "ServerBuilder"
    const val ARMERIA_HEADER_SCAN_LIMIT = 4096

    private val ARMERIA_REFERENCE_PATTERN =
        Regex("""(?<![\w"])com\.linecorp\.armeria(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")
    private val SERVER_BUILDER_IDENTIFIER = Regex("""(?<![\w"])serverBuilder(?![\w"])""")
    private val SERVER_BUILDER_CALL = Regex("""(?<![\w"])Server\.builder\(\)""")

    const val PATH_PREFIX_ANNOTATION = "com.linecorp.armeria.server.annotation.PathPrefix"
    const val DECORATOR_ANNOTATION = "com.linecorp.armeria.server.annotation.Decorator"
    const val EXCEPTION_HANDLER_ANNOTATION = "com.linecorp.armeria.server.annotation.ExceptionHandler"


    const val GET_ANNOTATION = "com.linecorp.armeria.server.annotation.Get"
    const val HEAD_ANNOTATION = "com.linecorp.armeria.server.annotation.Head"
    const val POST_ANNOTATION = "com.linecorp.armeria.server.annotation.Post"
    const val PUT_ANNOTATION = "com.linecorp.armeria.server.annotation.Put"
    const val DELETE_ANNOTATION = "com.linecorp.armeria.server.annotation.Delete"
    const val OPTIONS_ANNOTATION = "com.linecorp.armeria.server.annotation.Options"
    const val PATCH_ANNOTATION = "com.linecorp.armeria.server.annotation.Patch"
    const val TRACE_ANNOTATION = "com.linecorp.armeria.server.annotation.Trace"

    val routeAnnotations = mapOf(
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
        val names = when (value) {
            is PsiArrayInitializerMemberValue -> {
                extractStrings(value).ifEmpty {
                    value.initializers.map(::renderMemberValue)
                }
            }
            else -> extractStrings(value).ifEmpty { listOf(renderMemberValue(value)) }
        }
        return names.mapNotNull { it.takeIf(String::isNotBlank) }
    }

    fun extractStrings(value: PsiAnnotationMemberValue?): List<String> {
        return when (value) {
            null -> emptyList()
            is PsiLiteralExpression -> listOfNotNull(value.value as? String)
            is PsiArrayInitializerMemberValue -> value.initializers.flatMap(::extractStrings)
            else -> evaluateConstant(value)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun evaluateConstant(value: PsiAnnotationMemberValue): String? {
        val helper = JavaPsiFacade.getInstance(value.project).constantEvaluationHelper

        val result = helper.computeConstantExpression(value)

        return result as? String
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

    fun isServerBuilderType(typeText: String): Boolean {
        return typeText == SERVER_BUILDER_SIMPLE_NAME ||
            typeText == SERVER_BUILDER_CLASS ||
            typeText.endsWith(".$SERVER_BUILDER_SIMPLE_NAME")
    }

    fun referencesArmeriaInText(contents: CharSequence, scanLimit: Int = ARMERIA_HEADER_SCAN_LIMIT): Boolean {
        val searchWindow = contents.subSequence(0, minOf(contents.length, scanLimit))
        return ARMERIA_REFERENCE_PATTERN.containsMatchIn(searchWindow)
    }

    fun looksLikeServerBuilderReceiverText(text: String): Boolean {
        return SERVER_BUILDER_CALL.containsMatchIn(text) || SERVER_BUILDER_IDENTIFIER.containsMatchIn(text)
    }
}
