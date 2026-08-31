package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Reads `GrpcService` builder options used as Route Explorer badges:
 * `enableUnframedRequests` and `ProtoReflectionService` registration.
 */
object ArmeriaGrpcServiceOptionsSupport {
    private const val ENABLE_UNFRAMED_REQUESTS = "enableUnframedRequests"
    private val ADD_SERVICE_METHODS = setOf("addService", "addServices")
    private val REFLECTION_SIMPLE_NAMES = setOf("ProtoReflectionService", "ProtoReflectionServiceV1")

    fun contentHints(
        serviceExpression: PsiElement?,
        kind: KnownHttpServiceKind,
    ): List<String> {
        if (kind != KnownHttpServiceKind.GRPC) {
            return emptyList()
        }
        val options = collect(serviceExpression)
        return buildList {
            if (options.unframed) {
                add(message("route.explorer.badge.grpcUnframed"))
            }
            if (options.reflection) {
                add(message("route.explorer.badge.grpcReflection"))
            }
        }
    }

    fun hasUnframedHint(hints: List<String>): Boolean = message("route.explorer.badge.grpcUnframed") in hints

    fun hasReflectionHint(hints: List<String>): Boolean = message("route.explorer.badge.grpcReflection") in hints

    private fun collect(element: PsiElement?): GrpcServiceOptions {
        if (element == null) {
            return GrpcServiceOptions()
        }
        val partial =
            when (element) {
                is KtExpression -> collectKotlin(element, mutableSetOf())
                is PsiExpression -> collectJava(element, mutableSetOf())
                else -> PartialOptions()
            }
        return GrpcServiceOptions(
            unframed = partial.unframed == true,
            reflection = partial.reflection,
        )
    }

    private fun collectJava(
        expression: PsiExpression,
        visited: MutableSet<PsiElement>,
    ): PartialOptions {
        val unwrapped = unwrapJava(expression) ?: return PartialOptions()
        if (!visited.add(unwrapped)) {
            return PartialOptions()
        }
        return when (unwrapped) {
            is PsiMethodCallExpression -> {
                val fromCall = inspectJavaCall(unwrapped)
                val qualifier = unwrapped.methodExpression.qualifierExpression
                val fromQualifier = qualifier?.let { collectJava(it, visited) } ?: PartialOptions()
                fromCall.mergeInner(fromQualifier)
            }
            is PsiReferenceExpression -> {
                val resolved = unwrapped.resolve()
                if (resolved is PsiVariable) {
                    val initializer = resolved.initializer ?: return PartialOptions()
                    collectJava(initializer, visited)
                } else {
                    PartialOptions()
                }
            }
            is PsiNewExpression ->
                PartialOptions(reflection = looksLikeReflectionName(unwrapped.classReference?.referenceName))
            else -> PartialOptions()
        }
    }

    private fun inspectJavaCall(call: PsiMethodCallExpression): PartialOptions {
        val methodName = call.methodExpression.referenceName
        if (methodName == ENABLE_UNFRAMED_REQUESTS) {
            return PartialOptions(unframed = javaUnframedEnabled(call))
        }
        if (methodName in ADD_SERVICE_METHODS) {
            val reflection = call.argumentList.expressions.any(::javaLooksLikeReflection)
            return PartialOptions(reflection = reflection)
        }
        return PartialOptions()
    }

    private fun javaUnframedEnabled(call: PsiMethodCallExpression): Boolean {
        val argument = call.argumentList.expressions.firstOrNull() ?: return false
        return booleanValue(argument) ?: true
    }

    private fun booleanValue(expression: PsiExpression): Boolean? {
        val literal = unwrapJava(expression) as? PsiLiteralExpression ?: return null
        val value = literal.value
        if (value is Boolean) {
            return value
        }
        return when (literal.text) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun javaLooksLikeReflection(expression: PsiExpression): Boolean {
        if (looksLikeReflectionName(expression.text)) {
            return true
        }
        val target = ArmeriaRouteTargetExtractor.extractTarget(expression)
        return looksLikeReflectionName(target.substringAfterLast('.'))
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
                    return collectJava(initializer, visited)
                }
            }
        }
        return PartialOptions(reflection = looksLikeReflectionName(unwrapped.text))
    }

    private fun inspectKotlinCall(call: KtCallExpression): PartialOptions {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
        if (methodName == ENABLE_UNFRAMED_REQUESTS) {
            return PartialOptions(unframed = kotlinUnframedEnabled(call))
        }
        if (methodName in ADD_SERVICE_METHODS) {
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
        if (looksLikeReflectionName(expression.text)) {
            return true
        }
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: expression
        val call = kotlinCall(unwrapped)
        if (call != null) {
            val callee = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: call.text
            if (looksLikeReflectionName(callee)) {
                return true
            }
            val qualifier = kotlinQualifier(call)
            if (qualifier != null && looksLikeReflectionName(qualifier.text.substringAfterLast('.'))) {
                return true
            }
        }
        return looksLikeReflectionName(unwrapped.text.substringAfterLast('.'))
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

    private fun unwrapJava(expression: PsiExpression?): PsiExpression? {
        var current = expression ?: return null
        while (true) {
            current =
                when (current) {
                    is PsiParenthesizedExpression -> current.expression ?: return null
                    is PsiTypeCastExpression -> current.operand ?: return null
                    else -> return current
                }
        }
    }

    private fun looksLikeReflectionName(name: String?): Boolean {
        if (name.isNullOrBlank()) {
            return false
        }
        return REFLECTION_SIMPLE_NAMES.any { simpleName ->
            name == simpleName ||
                name.endsWith(".$simpleName") ||
                name.contains("$simpleName.") ||
                name.contains("$simpleName(")
        }
    }

    private data class GrpcServiceOptions(
        val unframed: Boolean = false,
        val reflection: Boolean = false,
    )

    private data class PartialOptions(
        val unframed: Boolean? = null,
        val reflection: Boolean = false,
    ) {
        fun mergeInner(inner: PartialOptions): PartialOptions =
            PartialOptions(
                unframed = unframed ?: inner.unframed,
                reflection = reflection || inner.reflection,
            )
    }
}
