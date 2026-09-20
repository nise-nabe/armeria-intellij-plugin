package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import scala.Option

/**
 * Shared helpers for the Scala inspections: route-annotation lookup and annotation path
 * extraction on top of the `PsiMethod`/`PsiAnnotation` adapters the Scala plugin provides.
 */
internal object ArmeriaScalaInspectionSupport {
    fun routeAnnotation(method: PsiMethod): Pair<PsiAnnotation, String>? =
        method.annotations.firstNotNullOfOrNull { candidate ->
            val qualifiedName = candidate.qualifiedName ?: return@firstNotNullOfOrNull null
            ArmeriaRouteSupport.routeAnnotations[qualifiedName]?.let { candidate to it }
        }

    fun methodRoute(method: PsiMethod): ArmeriaScalaMethodRoute? {
        val routeAnnotation = routeAnnotation(method) ?: return null
        val classPrefix = classPrefixOf(method)
        val rawPaths =
            buildList {
                addAll(annotationPaths(routeAnnotation.first))
                method.annotations
                    .filter { it.qualifiedName == ArmeriaRouteSupport.PATH_ANNOTATION }
                    .forEach { addAll(annotationPaths(it)) }
            }.ifEmpty { listOf("/") }
        val paths =
            rawPaths
                .map { rawPath -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, rawPath) }
                .distinct()
        return ArmeriaScalaMethodRoute(routeAnnotation.second, paths, classPrefix)
    }

    fun classPrefixOf(method: PsiMethod): String {
        val prefixAnnotation =
            method.containingClass
                ?.annotations
                ?.firstOrNull { it.qualifiedName == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION }
                ?: return ""
        return annotationPaths(prefixAnnotation).firstOrNull().orEmpty()
    }

    private fun annotationPaths(annotation: PsiAnnotation): List<String> =
        ArmeriaRouteSupport.extractPaths(annotation).ifEmpty {
            positionalStrings(annotation).map(::normalizeRoutePath)
        }

    /** Positional constructor argument when the annotation attribute does not resolve to a name. */
    private fun positionalStrings(annotation: PsiAnnotation): List<String> {
        val scalaAnnotation = annotation as? ScAnnotation ?: return emptyList()
        val arguments = scalaAnnotation.constructorInvocation().args().orNull() ?: return emptyList()
        val expressions = arguments.exprs().iterator()
        if (!expressions.hasNext()) {
            return emptyList()
        }
        return memberStrings(expressions.next() as? PsiAnnotationMemberValue)
    }

    private fun memberStrings(value: PsiAnnotationMemberValue?): List<String> =
        when (value) {
            null -> emptyList()
            is PsiLiteral -> listOfNotNull(value.value as? String)
            is PsiArrayInitializerMemberValue -> value.initializers.flatMap(::memberStrings)
            is MethodInvocation -> {
                val strings = mutableListOf<String>()
                val arguments = value.argumentExpressions().iterator()
                while (arguments.hasNext()) {
                    strings += memberStrings(arguments.next())
                }
                strings
            }
            else -> emptyList()
        }

    private fun normalizeRoutePath(path: String): String {
        val trimmed = path.trim()
        return if (hasPathTypePrefix(trimmed)) trimmed else ArmeriaRouteSupport.normalizePath(trimmed)
    }

    private fun hasPathTypePrefix(path: String): Boolean =
        path.startsWith("prefix:") ||
            path.startsWith("regex:") ||
            path.startsWith("glob:") ||
            path.startsWith("exact:")
}

internal data class ArmeriaScalaMethodRoute(
    val httpMethod: String,
    val paths: List<String>,
    val classPrefix: String,
)

internal fun <T> Option<T>.orNull(): T? = if (isDefined) get() else null
