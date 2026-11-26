package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiAnnotation
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaKotlinRouteCollector
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Kotlin-dependent annotation extraction. Callers must gate access with
 * [ArmeriaKotlinPluginSupport] so Kotlin PSI types are not resolved when the optional Kotlin
 * plugin is absent.
 */
internal object ArmeriaKotlinRouteAnnotationSupport {
    fun declaresUnresolvedPathArg(annotation: PsiAnnotation): Boolean? {
        val kotlinEntry = kotlinAnnotationEntry(annotation) ?: return null
        return kotlinEntry.valueArguments.any { argument ->
            val name = argument.getArgumentName()?.asName?.asString()
            (name == null || name == "value" || name == "path") &&
                ArmeriaKotlinRouteCollector
                    .extractKotlinStrings(argument.getArgumentExpression())
                    .isEmpty()
        }
    }

    /**
     * Kotlin light annotations expose their declared arguments through the source
     * [KtAnnotationEntry]; the light attribute value is null for non-literal arguments, so
     * paths are resolved through Kotlin PSI instead.
     */
    fun extractKotlinPaths(annotation: PsiAnnotation): List<String>? {
        val entry = kotlinAnnotationEntry(annotation) ?: return null
        return entry.valueArguments
            .filter { argument ->
                val name = argument.getArgumentName()?.asName?.asString()
                name == null || name == "value" || name == "path"
            }.flatMap { argument ->
                ArmeriaKotlinRouteCollector.extractKotlinStrings(argument.getArgumentExpression())
            }
    }

    private fun kotlinAnnotationEntry(annotation: PsiAnnotation): KtAnnotationEntry? {
        if (annotation.language.id != "kotlin") {
            return null
        }
        return (annotation as? KtLightElement<*, *>)?.kotlinOrigin as? KtAnnotationEntry
    }
}
