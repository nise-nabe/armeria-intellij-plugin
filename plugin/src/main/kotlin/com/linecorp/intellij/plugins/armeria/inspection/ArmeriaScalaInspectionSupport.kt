package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import scala.Option

/**
 * Shared helpers for the Scala inspections: route-annotation lookup and annotation path
 * extraction on top of the `PsiMethod`/`PsiAnnotation` adapters the Scala plugin provides.
 */
internal object ArmeriaScalaInspectionSupport {
    fun routeAnnotation(method: PsiMethod): Pair<PsiAnnotation, String>? = routeAnnotations(method).firstOrNull()

    fun routeAnnotations(method: PsiMethod): List<Pair<PsiAnnotation, String>> =
        method.annotations.mapNotNull { candidate ->
            val qualifiedName = candidate.qualifiedName ?: return@mapNotNull null
            ArmeriaRouteSupport.routeAnnotations[qualifiedName]?.let { candidate to it }
        }

    fun methodRoutes(method: PsiMethod): List<ArmeriaScalaMethodRoute> {
        val routeAnnotations = routeAnnotations(method)
        if (routeAnnotations.isEmpty()) {
            return emptyList()
        }
        val classPrefix = classPrefixOf(method)
        val pathAnnotations =
            method.annotations.filter { it.qualifiedName == ArmeriaRouteSupport.PATH_ANNOTATION }
        return routeAnnotations.mapNotNull { (routeAnnotation, httpMethod) ->
            // An annotation with arguments we could not resolve (e.g. a Scala constant
            // reference) must not collapse to "/" and produce a false duplicate.
            val ownPaths = annotationPaths(routeAnnotation)
            if (ownPaths.isEmpty() && hasArguments(routeAnnotation)) {
                return@mapNotNull null
            }
            val rawPaths =
                if (ownPaths.isNotEmpty()) {
                    // A path declared on the HTTP-method annotation cannot be combined
                    // with `@Path` (line/armeria#2853).
                    ownPaths
                } else {
                    var resolvable = true
                    val collected = mutableListOf<String>()
                    for (annotation in pathAnnotations) {
                        val paths = annotationPaths(annotation)
                        if (paths.isEmpty() && hasArguments(annotation)) {
                            resolvable = false
                            break
                        }
                        collected += paths
                    }
                    if (!resolvable) {
                        return@mapNotNull null
                    }
                    collected
                }
            val paths =
                rawPaths
                    .ifEmpty { listOf("/") }
                    .map { rawPath -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, rawPath) }
                    .distinct()
            ArmeriaScalaMethodRoute(httpMethod, paths)
        }
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

    /**
     * Direct supertypes resolved from the `extends`/`with` clause. `PsiClass.getSupers()` on a
     * Scala type definition goes through full Scala type inference and does not resolve Java
     * supertypes in light environments, so the syntactic template parents are used instead.
     */
    fun directSupers(psiClass: PsiClass): List<PsiClass> {
        if (psiClass !is ScTemplateDefinition) {
            return psiClass.supers.toList()
        }
        val parents = psiClass.extendsBlock().templateParents().orNull() ?: return emptyList()
        val result = mutableListOf<PsiClass>()
        val typeElements = parents.allTypeElements().iterator()
        while (typeElements.hasNext()) {
            var element: ScTypeElement = typeElements.next()
            while (element is ScParameterizedTypeElement) {
                element = element.typeElement()
            }
            val resolved =
                (element as? ScSimpleTypeElement)
                    ?.reference()
                    ?.orNull()
                    ?.bind()
                    ?.orNull()
                    ?.element()
            // A parent reference can bind to the Scala primary constructor instead of the
            // class itself; unwrap it through the containing class.
            val parentClass =
                when (resolved) {
                    is PsiClass -> resolved
                    is PsiMethod -> resolved.containingClass
                    else -> null
                }
            if (parentClass != null) {
                result += parentClass
            }
        }
        return result
    }

    fun hierarchyContains(
        start: PsiClass?,
        match: (PsiClass) -> Boolean,
    ): Boolean {
        val visited = mutableSetOf<PsiClass>()
        val queue = ArrayDeque<PsiClass>()
        if (start != null) {
            queue.add(start)
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            if (match(current)) {
                return true
            }
            queue.addAll(directSupers(current))
        }
        return false
    }

    private fun hasArguments(annotation: PsiAnnotation): Boolean {
        val scalaAnnotation = annotation as? ScAnnotation
        if (scalaAnnotation != null) {
            return scalaAnnotation
                .constructorInvocation()
                .args()
                .orNull()
                ?.exprs()
                ?.isEmpty == false
        }
        return annotation.parameterList.attributes.isNotEmpty()
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
)

internal fun <T> Option<T>.orNull(): T? = if (isDefined) get() else null
