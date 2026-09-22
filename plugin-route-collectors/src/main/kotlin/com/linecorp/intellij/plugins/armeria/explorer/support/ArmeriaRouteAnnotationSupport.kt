package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry

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

    const val PARAM_ANNOTATION = "com.linecorp.armeria.server.annotation.Param"
    const val HEADER_ANNOTATION = "com.linecorp.armeria.server.annotation.Header"
    const val COOKIE_ANNOTATION = "com.linecorp.armeria.server.annotation.Cookie"
    const val MATCHES_HEADER_ANNOTATION = "com.linecorp.armeria.server.annotation.MatchesHeader"
    const val MATCHES_PARAM_ANNOTATION = "com.linecorp.armeria.server.annotation.MatchesParam"
    const val CONSUMES_ANNOTATION = "com.linecorp.armeria.server.annotation.Consumes"
    const val PRODUCES_ANNOTATION = "com.linecorp.armeria.server.annotation.Produces"
    const val CONSUMES_JSON_ANNOTATION = "com.linecorp.armeria.server.annotation.ConsumesJson"
    const val PRODUCES_JSON_ANNOTATION = "com.linecorp.armeria.server.annotation.ProducesJson"
    const val PRODUCES_TEXT_ANNOTATION = "com.linecorp.armeria.server.annotation.ProducesText"
    const val PRODUCES_BINARY_ANNOTATION = "com.linecorp.armeria.server.annotation.ProducesBinary"
    const val PRODUCES_EVENT_STREAM_ANNOTATION = "com.linecorp.armeria.server.annotation.ProducesEventStream"
    const val DESCRIPTION_ANNOTATION = "com.linecorp.armeria.server.annotation.Description"
    const val DEFAULT_ANNOTATION = "com.linecorp.armeria.server.annotation.Default"
    const val ATTRIBUTE_ANNOTATION = "com.linecorp.armeria.server.annotation.Attribute"
    const val REQUEST_CONVERTER_ANNOTATION = "com.linecorp.armeria.server.annotation.RequestConverter"
    const val RESPONSE_CONVERTER_ANNOTATION = "com.linecorp.armeria.server.annotation.ResponseConverter"

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

    fun findRouteAnnotation(method: PsiMethod): Pair<PsiAnnotation, String>? = findRouteAnnotations(method).firstOrNull()

    fun findRouteAnnotations(method: PsiMethod): List<Pair<PsiAnnotation, String>> =
        method.modifierList.annotations.mapNotNull { candidate ->
            val qualifiedName = candidate.qualifiedName ?: return@mapNotNull null
            routeAnnotations[qualifiedName]?.let { candidate to it }
        }

    /**
     * `(httpMethod, paths)` pairs for every HTTP-method annotation on [method].
     *
     * Armeria binds one route per HTTP-method annotation, and a path declared on the
     * method annotation cannot be combined with `@Path` (line/armeria#1870,
     * line/armeria#2853), so the annotation's own paths take precedence and `@Path`
     * values apply only when the annotation declares none.
     */
    fun routeAnnotationPaths(method: PsiMethod): List<Pair<String, List<String>>> {
        val pathAnnotations = method.annotations.filter { it.qualifiedName == PATH_ANNOTATION }
        val pathAnnotationPaths = pathAnnotations.flatMap(::extractPaths)
        val pathArgsResolvable = pathAnnotations.none(::declaresUnresolvedPathArg)
        return findRouteAnnotations(method).mapNotNull { (annotation, httpMethod) ->
            val ownPaths = extractPaths(annotation)
            if (ownPaths.isEmpty() && declaresUnresolvedPathArg(annotation)) {
                // An annotation argument we cannot resolve must not collapse to "/"
                // and produce a false route (same guard as the Scala inspections).
                return@mapNotNull null
            }
            val rawPaths =
                ownPaths.ifEmpty {
                    if (!pathArgsResolvable) {
                        return@mapNotNull null
                    }
                    pathAnnotationPaths
                }
            httpMethod to rawPaths.ifEmpty { listOf("/") }.distinct()
        }
    }

    /**
     * True when the annotation declares `value`/`path` attributes that do not
     * resolve to constant strings. Declared name-value pairs are inspected rather
     * than `findDeclaredAttributeValue` because Kotlin light annotations expose a
     * `value` pair whose PSI value is null for unresolvable arguments.
     */
    fun declaresUnresolvedPathArg(annotation: PsiAnnotation): Boolean {
        val kotlinEntry = kotlinAnnotationEntry(annotation)
        if (kotlinEntry != null) {
            return kotlinEntry.valueArguments.any { argument ->
                val name = argument.getArgumentName()?.asName?.asString()
                (name == null || name == "value" || name == "path") &&
                    ArmeriaKotlinRouteCollector
                        .extractKotlinStrings(argument.getArgumentExpression())
                        .isEmpty()
            }
        }
        return annotation.parameterList.attributes.any { attribute ->
            val name = attribute.name ?: "value"
            (name == "value" || name == "path") && extractStrings(attribute.value).isEmpty()
        }
    }

    fun extractPaths(annotation: PsiAnnotation): List<String> {
        extractKotlinPaths(annotation)?.let { return it }
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

    /**
     * Kotlin light annotations expose their declared arguments through the source
     * [KtAnnotationEntry]; the light attribute value is null for non-literal
     * arguments, so paths are resolved through Kotlin PSI instead.
     */
    private fun extractKotlinPaths(annotation: PsiAnnotation): List<String>? {
        val entry = kotlinAnnotationEntry(annotation) ?: return null
        return entry.valueArguments
            .filter { argument ->
                val name = argument.getArgumentName()?.asName?.asString()
                name == null || name == "value" || name == "path"
            }.flatMap { argument ->
                ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
            }.map(::preserveOrNormalizePath)
    }

    private fun kotlinAnnotationEntry(annotation: PsiAnnotation): KtAnnotationEntry? {
        // org.jetbrains.kotlin is an optional plugin dependency — a Java-only
        // annotation must never trigger loading Kotlin PSI classes.
        if (annotation.language.id != "kotlin") {
            return null
        }
        return (annotation as? KtLightElement<*, *>)?.kotlinOrigin as? KtAnnotationEntry
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
            is PsiLiteral -> listOfNotNull(value.value as? String)
            is PsiArrayInitializerMemberValue -> value.initializers.flatMap(::extractStrings)
            is PsiExpression ->
                ArmeriaServerBuilderSupport
                    .extractJavaStringConstant(value)
                    ?.let { listOf(it) }
                    ?: emptyList()
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
