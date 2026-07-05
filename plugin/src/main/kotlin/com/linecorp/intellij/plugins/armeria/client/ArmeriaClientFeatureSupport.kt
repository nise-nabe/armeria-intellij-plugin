package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaDecoratorSupport
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTargetExtractor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression

internal object ArmeriaClientFeatureSupport {
    private val CLIENT_FEATURE_METHODS = setOf("decorator", "endpointGroup")

    fun extractJavaFeatures(expression: PsiMethodCallExpression): List<String> {
        val features = linkedSetOf<String>()
        val visited = mutableSetOf<PsiMethodCallExpression>()
        var call = findEnclosingQualifierCall(expression)
        while (call != null && visited.add(call)) {
            collectJavaFeature(call, features)
            call = findEnclosingQualifierCall(call)
        }
        return features.toList()
    }

    fun extractKotlinFeatures(call: KtCallExpression): List<String> {
        val features = linkedSetOf<String>()
        val visited = mutableSetOf<KtCallExpression>()
        var current: KtExpression = wrapCallExpression(call)
        var outer = findOuterFeatureCall(current)
        while (outer != null && visited.add(outer)) {
            collectKotlinFeature(outer, features)
            current = outer.parent as? KtDotQualifiedExpression ?: break
            outer = findOuterFeatureCall(current)
        }
        return features.toList()
    }

    private fun collectJavaFeature(call: PsiMethodCallExpression, features: MutableSet<String>) {
        when (call.methodExpression.referenceName) {
            "decorator" -> {
                val argument = call.argumentList.expressions.firstOrNull()
                val label = argument?.let(ArmeriaRouteTargetExtractor::extractTarget).orEmpty()
                ArmeriaDecoratorSupport.labelDecorator(label)
                    .takeIf { it.isNotBlank() }
                    ?.let(features::add)
            }
            "endpointGroup" -> {
                val argument = call.argumentList.expressions.firstOrNull()
                val label = argument?.let(ArmeriaRouteTargetExtractor::extractTarget).orEmpty().ifBlank { "EndpointGroup" }
                features += "EndpointGroup: $label"
            }
        }
    }

    private fun collectKotlinFeature(call: KtCallExpression, features: MutableSet<String>) {
        when (call.calleeExpression?.text) {
            "decorator" -> {
                val argument = call.valueArguments.firstOrNull()?.getArgumentExpression()
                ArmeriaDecoratorSupport.labelDecorator(argument?.text.orEmpty())
                    .takeIf { it.isNotBlank() }
                    ?.let(features::add)
            }
            "endpointGroup" -> {
                val argument = call.valueArguments.firstOrNull()?.getArgumentExpression()
                val label = argument?.text?.takeIf { it.isNotBlank() } ?: "EndpointGroup"
                features += "EndpointGroup: $label"
            }
        }
    }

    private fun findEnclosingQualifierCall(expression: PsiExpression): PsiMethodCallExpression? {
        var element: PsiElement? = expression.parent
        while (element != null) {
            if (element is PsiMethodCallExpression && element.methodExpression.qualifierExpression == expression) {
                return element
            }
            element = element.parent
        }
        return null
    }

    private fun wrapCallExpression(call: KtCallExpression): KtExpression {
        val parentDot = generateSequence(call.parent) { it.parent }
            .filterIsInstance<KtDotQualifiedExpression>()
            .firstOrNull { it.selectorExpression == call }
        return parentDot ?: call
    }

    private fun findOuterFeatureCall(expression: KtExpression): KtCallExpression? {
        val parentDot = generateSequence(expression.parent) { it.parent }
            .filterIsInstance<KtDotQualifiedExpression>()
            .firstOrNull { it.receiverExpression == expression }
            ?: return null
        val selector = parentDot.selectorExpression as? KtCallExpression ?: return null
        val methodName = selector.calleeExpression?.text ?: return null
        return selector.takeIf { methodName in CLIENT_FEATURE_METHODS }
    }
}
