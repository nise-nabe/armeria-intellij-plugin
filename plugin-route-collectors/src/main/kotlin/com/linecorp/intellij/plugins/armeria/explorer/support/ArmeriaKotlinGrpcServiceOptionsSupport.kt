package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Kotlin-dependent gRPC service option extraction. Callers must gate access with
 * [ArmeriaKotlinPluginSupport] so Kotlin PSI types are not resolved when the optional Kotlin
 * plugin is absent.
 */
internal object ArmeriaKotlinGrpcServiceOptionsSupport {
    private typealias PartialOptions = ArmeriaGrpcServiceOptionsSupport.PartialOptions

    fun collect(
        element: PsiElement,
        visited: MutableSet<PsiElement>,
    ): PartialOptions {
        val expression = element as? KtExpression ?: return PartialOptions()
        return collectKotlin(expression, visited)
    }

    private fun collectKotlin(
        expression: KtExpression,
        visited: MutableSet<PsiElement>,
    ): PartialOptions {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return PartialOptions()
        if (!visited.add(unwrapped)) {
            return PartialOptions()
        }
        val call = kotlinCall(unwrapped)
        if (call != null) {
            val fromCall = inspectKotlinCall(call)
            val qualifier = kotlinQualifier(call)
            val fromQualifier = qualifier?.let { collectKotlin(it, visited) } ?: PartialOptions()
            return fromCall.mergeInner(fromQualifier)
        }
        if (unwrapped is KtNameReferenceExpression) {
            when (val resolved = unwrapped.references.firstOrNull()?.resolve()) {
                is KtProperty -> {
                    val initializer = resolved.initializer ?: return PartialOptions()
                    return collectKotlin(initializer, visited)
                }

                is PsiVariable -> {
                    val initializer = resolved.initializer ?: return PartialOptions()
                    return ArmeriaGrpcServiceOptionsSupport.collectJava(initializer, visited)
                }
            }
        }
        return PartialOptions(reflection = ArmeriaGrpcServiceOptionsSupport.looksLikeReflectionName(unwrapped.text))
    }

    private fun inspectKotlinCall(call: KtCallExpression): PartialOptions {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
        if (methodName == ArmeriaGrpcServiceOptionsSupport.ENABLE_UNFRAMED_REQUESTS) {
            return PartialOptions(unframed = kotlinUnframedEnabled(call))
        }
        if (methodName in ArmeriaGrpcServiceOptionsSupport.ADD_SERVICE_METHODS) {
            val reflection =
                call.valueArguments
                    .mapNotNull { it.getArgumentExpression() }
                    .any(::kotlinLooksLikeReflection)
            return PartialOptions(reflection = reflection)
        }
        return PartialOptions()
    }

    private fun kotlinUnframedEnabled(call: KtCallExpression): Boolean {
        val argument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return false
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(argument) ?: argument
        return when (unwrapped.text) {
            "true" -> true
            "false" -> false
            else -> true
        }
    }

    private fun kotlinLooksLikeReflection(expression: KtExpression): Boolean {
        if (ArmeriaGrpcServiceOptionsSupport.looksLikeReflectionName(expression.text)) {
            return true
        }
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        val call = kotlinCall(unwrapped)
        if (call != null) {
            val callee = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: call.text
            if (ArmeriaGrpcServiceOptionsSupport.looksLikeReflectionName(callee)) {
                return true
            }
            val qualifier = kotlinQualifier(call)
            if (qualifier != null &&
                ArmeriaGrpcServiceOptionsSupport.looksLikeReflectionName(qualifier.text.substringAfterLast('.'))
            ) {
                return true
            }
        }
        return ArmeriaGrpcServiceOptionsSupport.looksLikeReflectionName(unwrapped.text.substringAfterLast('.'))
    }

    private fun kotlinCall(expression: KtExpression): KtCallExpression? =
        when (val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression) {
            is KtCallExpression -> unwrapped
            is KtDotQualifiedExpression -> unwrapped.selectorExpression as? KtCallExpression
            else -> null
        }

    private fun kotlinQualifier(call: KtCallExpression): KtExpression? {
        val callee = call.calleeExpression
        return when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (call.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
    }
}
