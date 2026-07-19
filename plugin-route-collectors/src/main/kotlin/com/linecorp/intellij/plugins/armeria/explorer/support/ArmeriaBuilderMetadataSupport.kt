package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaKotlinTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaKotlinDecoratorSupport

/**
 * Language-neutral dispatch for programmatic-decorator and timeout metadata that runs during
 * service-registration collection. Java and Kotlin implementations live in
 * `explorer.collector.decorator`/`explorer.collector.annotation`; keeping the dispatch in
 * `support` lets [plugin-route-collectors] populate these fields without depending on the
 * analysis façade.
 */
object ArmeriaBuilderMetadataSupport {
    fun collectProgrammaticDecorators(
        element: PsiElement,
        registrationPath: String,
    ): List<String> {
        if (element is PsiMethodCallExpression) {
            return ArmeriaDecoratorSupport.collectProgrammaticDecorators(element, registrationPath)
        }
        if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
            return ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(element, registrationPath)
        }
        return emptyList()
    }

    fun collectBuilderTimeoutHints(element: PsiElement): List<String> {
        if (element is PsiMethodCallExpression) {
            return ArmeriaTimeoutSupport.collectBuilderTimeoutHints(element)
        }
        if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
            return ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(element)
        }
        return emptyList()
    }
}
