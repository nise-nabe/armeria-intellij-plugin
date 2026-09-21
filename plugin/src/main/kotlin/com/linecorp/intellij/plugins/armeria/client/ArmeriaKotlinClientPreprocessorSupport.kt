package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/**
 * Kotlin counterpart of [ArmeriaClientPreprocessorSupport] — labels the EndpointGroup wrapped
 * by an Armeria preprocessor factory call (`HttpPreprocessor.of(protocol, endpointGroup)` /
 * `RpcPreprocessor.of(...)`) passed as a client factory argument.
 */
internal object ArmeriaKotlinClientPreprocessorSupport {
    private const val FACTORY_METHOD = "of"

    fun labelKotlinPreprocessorFactory(expression: KtExpression?): String? = labelKotlinPreprocessorFactory(expression, mutableSetOf())

    private fun labelKotlinPreprocessorFactory(
        expression: KtExpression?,
        visited: MutableSet<PsiElement>,
    ): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val call = ArmeriaKotlinClientCollector.callExpressionInChain(unwrapped)
        if (call != null) {
            return labelKotlinPreprocessorCall(call)
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
                    labelKotlinPreprocessorFactory(resolved.initializer, visited)
                } else {
                    null
                }
            is PsiVariable ->
                if (visited.add(resolved)) {
                    ArmeriaClientPreprocessorSupport.labelJavaPreprocessorFactory(resolved.initializer, visited)
                } else {
                    null
                }
            else -> null
        }
    }

    private fun labelKotlinPreprocessorCall(call: KtCallExpression): String? {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) != FACTORY_METHOD) {
            return null
        }
        val qualifiedName = ArmeriaKotlinClientXdsSupport.resolveKotlinFactoryClass(call)
        if (qualifiedName != null) {
            if (!ArmeriaClientPreprocessorSupport.isArmeriaPreprocessorClass(qualifiedName)) {
                return null
            }
        } else {
            val receiver = (call.parent as? KtQualifiedExpression)?.receiverExpression ?: return null
            val resolvedName =
                when (val resolved = lastNameReference(receiver)?.references?.firstOrNull()?.resolve()) {
                    is PsiClass -> resolved.qualifiedName
                    is KtClassOrObject -> resolved.fqName?.asString()
                    else ->
                        receiver.text.takeIf {
                            ArmeriaClientPreprocessorSupport.isArmeriaPreprocessorClass(it)
                        }
                }
            if (!ArmeriaClientPreprocessorSupport.isArmeriaPreprocessorClass(resolvedName)) {
                return null
            }
        }
        return call.valueArguments.firstNotNullOfOrNull { argument ->
            ArmeriaKotlinClientEndpointGroupSupport.labelKotlinEndpointGroup(argument.getArgumentExpression())
        }
    }

    private fun lastNameReference(expression: KtExpression?): KtNameReferenceExpression? =
        when (expression) {
            is KtNameReferenceExpression -> expression
            is KtQualifiedExpression -> lastNameReference(expression.selectorExpression)
            else -> null
        }
}
