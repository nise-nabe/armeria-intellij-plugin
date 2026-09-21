package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

internal data class ArmeriaKotlinMethodRoute(
    val httpMethod: String,
    val paths: List<String>,
    val rawPaths: List<String>,
    val classPrefix: String,
) {
    companion object {
        fun from(function: KtNamedFunction): ArmeriaKotlinMethodRoute? = all(function).firstOrNull()

        /**
         * One route per HTTP-method annotation entry. Armeria binds a route for each
         * annotation, and a path declared on the annotation cannot be combined with
         * `@Path` (line/armeria#1870, line/armeria#2853), so the entry's own paths take
         * precedence and `@Path` values apply only when the entry declares none.
         */
        fun all(function: KtNamedFunction): List<ArmeriaKotlinMethodRoute> {
            val methodAnnotations =
                function.annotationEntries.mapNotNull { entry ->
                    val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: return@mapNotNull null
                    val method = ArmeriaRouteSupport.routeAnnotations[qualifiedName] ?: return@mapNotNull null
                    entry to method
                }
            if (methodAnnotations.isEmpty()) {
                return emptyList()
            }
            val classPrefix =
                PsiTreeUtil
                    .getParentOfType(function, KtClassOrObject::class.java)
                    ?.annotationEntries
                    ?.firstOrNull {
                        ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION
                    }?.let(::extractPathPrefix)
                    .orEmpty()
            val pathEntries =
                function.annotationEntries
                    .filter { ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PATH_ANNOTATION }
            val pathAnnotationPaths = pathEntries.flatMap(::extractPaths)
            val pathArgsResolvable = pathEntries.none(::declaresUnresolvedPathArg)
            return methodAnnotations.mapNotNull { (entry, httpMethod) ->
                val ownPaths = extractPaths(entry)
                if (ownPaths.isEmpty() && declaresUnresolvedPathArg(entry)) {
                    // Unresolvable path arguments must not collapse to "/" and
                    // produce a false duplicate (same guard as Scala).
                    return@mapNotNull null
                }
                val rawPaths =
                    ownPaths
                        .ifEmpty {
                            if (!pathArgsResolvable) {
                                return@mapNotNull null
                            }
                            pathAnnotationPaths
                        }.ifEmpty { listOf("/") }
                val paths =
                    rawPaths
                        .map { rawPath -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, rawPath) }
                        .distinct()
                ArmeriaKotlinMethodRoute(httpMethod, paths, rawPaths, classPrefix)
            }
        }

        /**
         * True when the entry declares positional/`value`/`path` arguments that do
         * not resolve to constant strings.
         */
        private fun declaresUnresolvedPathArg(entry: KtAnnotationEntry): Boolean =
            entry.valueArguments.any { argument ->
                val name = argument.getArgumentName()?.asName?.asString()
                (name == null || name == "value" || name == "path") &&
                    ArmeriaKotlinRouteCollector
                        .extractKotlinStrings(argument.getArgumentExpression())
                        .isEmpty()
            }

        private fun extractPathPrefix(annotation: KtAnnotationEntry): String = extractPaths(annotation).firstOrNull().orEmpty()

        private fun extractPaths(annotation: KtAnnotationEntry): List<String> {
            val valuePaths = ArmeriaKotlinAnnotationSupport.extractStrings(annotation, "value")
            if (valuePaths.isNotEmpty()) {
                return valuePaths.map(::preserveOrNormalizePath)
            }
            val pathPaths = ArmeriaKotlinAnnotationSupport.extractStrings(annotation, "path")
            if (pathPaths.isNotEmpty()) {
                return pathPaths.map(::preserveOrNormalizePath)
            }
            return emptyList()
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
    }
}
