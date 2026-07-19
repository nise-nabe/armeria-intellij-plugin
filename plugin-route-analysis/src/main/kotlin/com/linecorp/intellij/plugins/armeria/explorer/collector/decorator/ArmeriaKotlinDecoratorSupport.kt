package com.linecorp.intellij.plugins.armeria.explorer.collector.decorator
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression

internal object ArmeriaKotlinDecoratorSupport {
    fun collectProgrammaticDecorators(
        element: PsiElement,
        registrationPath: String,
    ): List<String> {
        val registrationCall = element as? KtCallExpression ?: return emptyList()
        return collectKotlinDecoratorsOnBuilderChain(registrationCall, registrationPath)
    }

    private fun collectKotlinDecoratorsOnBuilderChain(
        registrationCall: KtCallExpression,
        registrationPath: String,
    ): List<String> {
        val candidates = linkedSetOf<ArmeriaDecoratorSupport.DecoratorCandidate>()
        ArmeriaKotlinDecoratorChainSupport.collectKotlinDecoratorsFromReceiver(registrationCall, candidates)
        ArmeriaKotlinDecoratorScopeSupport.collectKotlinDecoratorsFromPrecedingScopeBlocks(registrationCall, candidates)
        ArmeriaKotlinDecoratorScopeSupport.collectKotlinDecoratorsInScopeLambda(registrationCall, candidates)
        ArmeriaKotlinDecoratorScopeSupport.enclosingBuilderScopeCall(registrationCall)?.let { scopeCall ->
            ArmeriaKotlinDecoratorScopeSupport.collectKotlinDecoratorsFromPrecedingScopeBlocks(scopeCall, candidates)
        }
        return ArmeriaDecoratorSupport.filterDecoratorCandidates(candidates, registrationPath)
    }
}
