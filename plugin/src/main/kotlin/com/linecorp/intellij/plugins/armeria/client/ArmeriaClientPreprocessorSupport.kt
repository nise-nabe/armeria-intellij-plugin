package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable

/**
 * Labels the EndpointGroup wrapped by an Armeria preprocessor factory
 * (`HttpPreprocessor.of(protocol, endpointGroup)` / `RpcPreprocessor.of(...)`) when the
 * preprocessor call is passed as a client factory argument.
 */
internal object ArmeriaClientPreprocessorSupport {
    private val PREPROCESSOR_CLASS_NAMES =
        setOf(
            "com.linecorp.armeria.client.HttpPreprocessor",
            "com.linecorp.armeria.client.RpcPreprocessor",
        )

    private const val FACTORY_METHOD = "of"

    fun labelJavaPreprocessorFactory(expression: PsiExpression?): String? = labelJavaPreprocessorFactory(expression, mutableSetOf())

    internal fun labelJavaPreprocessorFactory(
        expression: PsiExpression?,
        visited: MutableSet<PsiElement>,
    ): String? =
        when (expression) {
            null -> null
            is PsiMethodCallExpression -> {
                if (expression.methodExpression.referenceName != FACTORY_METHOD) {
                    return null
                }
                if (!isArmeriaPreprocessorCall(expression)) {
                    return null
                }
                expression.argumentList.expressions.firstNotNullOfOrNull {
                    ArmeriaClientEndpointGroupSupport.labelJavaEndpointGroup(it, visited)
                }
            }
            is PsiParenthesizedExpression -> labelJavaPreprocessorFactory(expression.expression, visited)
            is PsiReferenceExpression -> {
                val resolved = expression.resolve() as? PsiVariable ?: return null
                if (!visited.add(resolved)) {
                    return null
                }
                labelJavaPreprocessorFactory(resolved.initializer, visited)
            }
            else -> null
        }

    internal fun isArmeriaPreprocessorClass(qualifiedName: String?): Boolean = qualifiedName in PREPROCESSOR_CLASS_NAMES

    private fun isArmeriaPreprocessorCall(call: PsiMethodCallExpression): Boolean {
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            return isArmeriaPreprocessorClass(resolvedClass)
        }
        val receiverClass =
            (call.methodExpression.qualifierExpression as? PsiReferenceExpression)
                ?.resolve() as? PsiClass ?: return false
        return isArmeriaPreprocessorClass(receiverClass.qualifiedName)
    }
}
