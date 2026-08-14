package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object ArmeriaKotlinAnnotationSupport {
    fun qualifiedName(entry: KtAnnotationEntry): String? {
        resolveAnnotationType(entry)?.let { return it }
        val shortName = entry.shortName?.asString() ?: return null
        entry.containingKtFile.importDirectives
            .mapNotNull { it.importPath?.pathStr }
            .firstOrNull { it == shortName || it.endsWith(".$shortName") }
            ?.let { return it }
        return entry.containingKtFile.declarations
            .filterIsInstance<KtClass>()
            .firstOrNull { it.name == shortName }
            ?.fqName
            ?.asString()
    }

    fun extractStrings(
        entry: KtAnnotationEntry,
        attributeName: String = "value",
    ): List<String> {
        val named =
            entry.valueArguments
                .filter { it.getArgumentName()?.asName?.asString() == attributeName }
                .flatMap { argument ->
                    ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
                }
        if (named.isNotEmpty()) {
            return named
        }
        if (attributeName != "value") {
            return emptyList()
        }
        return entry.valueArguments
            .filter { it.getArgumentName() == null }
            .flatMap { argument ->
                ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
            }
    }

    fun pathVariables(function: KtNamedFunction): Set<String> {
        val route = ArmeriaKotlinMethodRoute.from(function) ?: return emptySet()
        return buildSet {
            addAll(ArmeriaPathVariableSupport.extractPathVariables(route.classPrefix))
            route.rawPaths.forEach { addAll(ArmeriaPathVariableSupport.extractPathVariables(it)) }
        }
    }

    fun paramBindings(function: KtNamedFunction): List<ArmeriaParamBinding> =
        function.valueParameters.mapNotNull { parameter ->
            val entry =
                parameter.annotationEntries.firstOrNull {
                    qualifiedName(it) == ArmeriaRouteSupport.PARAM_ANNOTATION
                } ?: return@mapNotNull null
            val explicit = extractStrings(entry).firstOrNull { it.isNotBlank() }
            val name = explicit ?: parameter.name ?: return@mapNotNull null
            ArmeriaParamBinding(name, entry.calleeExpression ?: entry)
        }

    private fun resolveAnnotationType(entry: KtAnnotationEntry): String? {
        val candidates =
            listOfNotNull(
                entry.typeReference
                    ?.references
                    ?.firstOrNull()
                    ?.resolve(),
                entry.calleeExpression
                    ?.references
                    ?.firstOrNull()
                    ?.resolve(),
            )
        for (resolved in candidates) {
            when (resolved) {
                is PsiClass -> resolved.qualifiedName?.let { return it }
                is KtClass -> resolved.fqName?.asString()?.let { return it }
            }
        }
        return null
    }
}
