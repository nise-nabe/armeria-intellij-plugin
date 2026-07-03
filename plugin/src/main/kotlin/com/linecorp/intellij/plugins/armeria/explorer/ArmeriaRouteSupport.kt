package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiVariable

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
    const val ARMERIA_SPRING_PACKAGE_PREFIX = "com.linecorp.armeria.spring"
    const val ARMERIA_SERVER_PACKAGE_PREFIX = "com.linecorp.armeria.server"
    const val ARMERIA_SERVER_CLASS = "com.linecorp.armeria.server.Server"
    const val ARMERIA_SERVER_CONFIGURATOR_CLASS = "$ARMERIA_SPRING_PACKAGE_PREFIX.ArmeriaServerConfigurator"
    const val SERVER_BUILDER_CLASS = "com.linecorp.armeria.server.ServerBuilder"
    const val SPRING_BEAN_ANNOTATION = "org.springframework.context.annotation.Bean"
    const val SERVER_BUILDER_SIMPLE_NAME = "ServerBuilder"
    const val ARMERIA_HEADER_SCAN_LIMIT = 4096

    private const val FQCN_SERVER_BUILDER_CALL_PATTERN =
        """(?<![\w"])com\.linecorp\.armeria\.server\.Server\.builder\(\)"""
    private const val UNQUALIFIED_SERVER_BUILDER_CALL_PATTERN =
        """(?<![.\w"])Server\.builder\(\)"""

    private val ARMERIA_REFERENCE_PATTERN =
        Regex("""(?<![\w"])com\.linecorp\.armeria(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")
    private val SERVER_BUILDER_IDENTIFIER = Regex("""(?<![\w"])serverBuilder(?![\w"])""")
    private val FQCN_SERVER_BUILDER_CALL = Regex(FQCN_SERVER_BUILDER_CALL_PATTERN)
    private val UNQUALIFIED_SERVER_BUILDER_CALL = Regex(UNQUALIFIED_SERVER_BUILDER_CALL_PATTERN)
    private val SERVER_BUILDER_CALL =
        Regex("""(?:$FQCN_SERVER_BUILDER_CALL_PATTERN|$UNQUALIFIED_SERVER_BUILDER_CALL_PATTERN)""")

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

    fun decoratorPathPatternAppliesToRoute(pattern: String, routePath: String): Boolean {
        val normalizedPattern = normalizePath(pattern.trim().trim('"'))
        val normalizedRoute = normalizePath(routePath)
        if (normalizedPattern.endsWith("/**")) {
            val prefix = normalizedPattern.removeSuffix("/**")
            return normalizedRoute == prefix || normalizedRoute.startsWith("$prefix/")
        }
        if (normalizedPattern.endsWith("/*")) {
            val prefix = normalizedPattern.removeSuffix("/*")
            if (normalizedRoute == prefix) {
                return false
            }
            if (!normalizedRoute.startsWith("$prefix/")) {
                return false
            }
            val remainder = normalizedRoute.removePrefix("$prefix/").trimStart('/')
            return remainder.isNotEmpty() && !remainder.contains('/')
        }
        return normalizedRoute == normalizedPattern
    }

    fun isSpringBootArmeriaAvailable(psiFacade: JavaPsiFacade, scope: GlobalSearchScope): Boolean {
        if (psiFacade.findClass(SPRING_BEAN_ANNOTATION, scope) == null) {
            return false
        }
        return psiFacade.findClass(ARMERIA_SERVER_CONFIGURATOR_CLASS, scope) != null ||
            psiFacade.findClass(SERVER_BUILDER_CLASS, scope) != null ||
            psiFacade.findClass(ARMERIA_SERVER_CLASS, scope) != null
    }

    fun isArmeriaServerBeanReturnType(method: PsiMethod, scope: GlobalSearchScope): Boolean {
        val returnType = method.returnType ?: return false
        val psiClass = (returnType as? PsiClassType)?.resolve()
        if (psiClass != null) {
            return isArmeriaServerBeanReturnType(psiClass, JavaPsiFacade.getInstance(method.project), scope)
        }
        return isArmeriaServerBeanReturnType(returnType.canonicalText)
    }

    fun isArmeriaServerBeanReturnType(returnType: String): Boolean {
        if (returnType == ARMERIA_SERVER_CLASS || isServerBuilderType(returnType)) {
            return true
        }
        return returnType == ARMERIA_SERVER_CONFIGURATOR_CLASS
    }

    private fun isArmeriaServerBeanReturnType(
        psiClass: PsiClass,
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
    ): Boolean {
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName == ARMERIA_SERVER_CLASS ||
            qualifiedName == ARMERIA_SERVER_CONFIGURATOR_CLASS ||
            isServerBuilderType(qualifiedName.orEmpty())
        ) {
            return true
        }
        val configuratorClass = psiFacade.findClass(ARMERIA_SERVER_CONFIGURATOR_CLASS, scope)
        if (configuratorClass != null && psiClass.isInheritor(configuratorClass, true)) {
            return true
        }
        val serverClass = psiFacade.findClass(ARMERIA_SERVER_CLASS, scope)
        if (serverClass != null && psiClass.isInheritor(serverClass, true)) {
            return true
        }
        val serverBuilderClass = psiFacade.findClass(SERVER_BUILDER_CLASS, scope)
        return serverBuilderClass != null && psiClass.isInheritor(serverBuilderClass, true)
    }

    fun isServerBuilderType(typeText: String): Boolean {
        val normalized = normalizeServerBuilderTypeText(typeText)
        return normalized == SERVER_BUILDER_SIMPLE_NAME ||
            normalized == SERVER_BUILDER_CLASS ||
            normalized.endsWith(".$SERVER_BUILDER_SIMPLE_NAME")
    }

    fun evaluateJavaStringConstant(variable: PsiVariable): String? {
        (variable as? PsiField)?.initializer?.let { initializer ->
            if (initializer is PsiLiteralExpression) {
                return initializer.value as? String
            }
        }
        return variable.computeConstantValue() as? String
    }

    private fun normalizeServerBuilderTypeText(typeText: String): String {
        var normalized = typeText.trim().replace(Regex("""/\*.*?\*/"""), "").trim()
        normalized = stripLeadingKotlinTypeAnnotations(normalized)
        if (normalized.endsWith('?')) {
            normalized = normalized.dropLast(1).trim()
        }
        val genericStart = normalized.indexOf('<')
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart).trim()
        }
        return normalized
    }

    private fun stripLeadingKotlinTypeAnnotations(typeText: String): String {
        var remaining = typeText.trimStart()
        while (remaining.startsWith('@')) {
            var index = 1
            var depth = 0
            while (index < remaining.length) {
                when (val char = remaining[index]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            index++
                            break
                        }
                    }
                    ' ', '\t', '\n' -> if (depth == 0) {
                        break
                    }
                }
                index++
            }
            remaining = remaining.substring(index).trimStart()
        }
        return remaining
    }

    fun referencesArmeriaInText(contents: CharSequence, scanLimit: Int = ARMERIA_HEADER_SCAN_LIMIT): Boolean {
        val searchWindow = contents.subSequence(0, minOf(contents.length, scanLimit))
        return ARMERIA_REFERENCE_PATTERN.containsMatchIn(searchWindow)
    }

    fun looksLikeServerBuilderReceiverText(text: String): Boolean {
        return SERVER_BUILDER_CALL.containsMatchIn(text) || SERVER_BUILDER_IDENTIFIER.containsMatchIn(text)
    }

    fun referencesArmeriaApplicationInSource(contents: CharSequence): Boolean {
        if (FQCN_SERVER_BUILDER_CALL.containsMatchIn(contents)) {
            return true
        }
        return referencesArmeriaInText(contents) &&
            UNQUALIFIED_SERVER_BUILDER_CALL.containsMatchIn(contents)
    }
}
