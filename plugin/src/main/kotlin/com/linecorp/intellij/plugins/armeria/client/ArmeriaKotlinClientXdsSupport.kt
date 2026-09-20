package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression

internal object ArmeriaKotlinClientXdsSupport {
    /** Receiver class FQN of a Kotlin static factory call, when resolvable. */
    fun resolveKotlinFactoryClass(call: KtCallExpression): String? = resolveKotlinFactoryMethod(call)?.containingClass?.qualifiedName

    private fun resolveKotlinFactoryMethod(call: KtCallExpression): PsiMethod? =
        call.calleeExpression
            ?.references
            ?.firstNotNullOfOrNull { it.resolve() as? PsiMethod }

    fun labelKotlinXdsFactory(expression: KtExpression?): String? = labelKotlinXdsFactory(expression, mutableSetOf())

    private fun labelKotlinXdsFactory(
        expression: KtExpression?,
        visited: MutableSet<PsiElement>,
    ): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val call = ArmeriaKotlinClientCollector.callExpressionInChain(unwrapped)
        if (call != null) {
            return labelKotlinXdsCall(call)
        }
        val reference =
            when (unwrapped) {
                is KtNameReferenceExpression -> unwrapped
                is KtQualifiedExpression -> unwrapped.selectorExpression as? KtNameReferenceExpression
                else -> null
            } ?: return null
        return when (val resolved = reference.references.firstOrNull()?.resolve()) {
            is KtProperty ->
                if (visited.add(resolved)) {
                    labelKotlinXdsFactory(resolved.initializer, visited)
                } else {
                    null
                }
            is PsiVariable ->
                if (visited.add(resolved)) {
                    ArmeriaClientXdsSupport.labelJavaXdsFactory(resolved.initializer, visited)
                } else {
                    null
                }
            else -> null
        }
    }

    private fun labelKotlinXdsCall(call: KtCallExpression): String? {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
        if (methodName !in ArmeriaClientXdsSupport.XDS_FACTORY_METHOD_NAMES) {
            return null
        }
        val method = resolveKotlinFactoryMethod(call)
        if (method != null) {
            if (!ArmeriaClientXdsSupport.isArmeriaXdsClass(method.containingClass?.qualifiedName)) {
                return null
            }
            return ArmeriaClientXdsSupport.xdsLabel(extractKotlinListenerName(call, method))
        }
        val receiver = (call.parent as? KtQualifiedExpression)?.receiverExpression ?: return null
        val resolved = receiver.references.firstOrNull()?.resolve() ?: return null
        val qualifiedName =
            when (resolved) {
                is PsiClass -> resolved.qualifiedName
                is KtClassOrObject -> resolved.fqName?.asString()
                else -> null
            }
        if (!ArmeriaClientXdsSupport.isArmeriaXdsClass(qualifiedName)) {
            return null
        }
        val fallback =
            (resolved as? PsiClass)?.let {
                ArmeriaClientXdsSupport.listenerNameMethod(it, methodName, call.valueArguments.size)
            }
        return ArmeriaClientXdsSupport.xdsLabel(
            fallback?.let { extractKotlinListenerName(call, it) }
                ?: call.valueArguments.asReversed().firstNotNullOfOrNull { argument ->
                    ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument.getArgumentExpression())
                },
        )
    }

    private fun extractKotlinListenerName(
        call: KtCallExpression,
        method: PsiMethod,
    ): String? {
        val parameters = method.parameterList.parameters
        val listenerIndex = parameters.indexOfFirst { it.name == ArmeriaClientXdsSupport.LISTENER_NAME_PARAMETER }
        if (listenerIndex >= 0) {
            val argument =
                ArmeriaKotlinExpressionSupport.findArgumentExpression(
                    call.valueArguments,
                    ArmeriaClientXdsSupport.LISTENER_NAME_PARAMETER,
                    listenerIndex,
                )
            ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument)?.let { return it }
        }
        return call.valueArguments.asReversed().firstNotNullOfOrNull { argument ->
            ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument.getArgumentExpression())
        }
    }
}
