package com.linecorp.intellij.plugins.armeria.explorer.collector.registration

import com.intellij.psi.PsiElement

/**
 * Cycle guard for Route-value walks. A declaration is in progress only while its
 * initializer or body is being resolved, so a later read of the same variable
 * reuses the route instead of failing the whole method.
 */
internal class RouteValueResolution {
    private val inProgress = mutableSetOf<PsiElement>()
    private val memo = mutableMapOf<PsiElement, FluentRouteChainInfo>()

    fun cached(
        element: PsiElement,
        target: String,
    ): FluentRouteChainInfo? = memo[element]?.copy(target = target)

    fun enter(
        element: PsiElement,
        hops: Int,
        maxHops: Int,
    ): Boolean {
        if (hops >= maxHops) {
            return false
        }
        return inProgress.add(element)
    }

    fun exit(
        element: PsiElement,
        result: FluentRouteChainInfo?,
    ) {
        inProgress.remove(element)
        if (result != null) {
            memo[element] = result
        }
    }
}
