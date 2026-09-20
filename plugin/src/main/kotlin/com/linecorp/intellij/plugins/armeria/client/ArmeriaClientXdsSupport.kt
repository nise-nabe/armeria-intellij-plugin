package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaClientXdsSupport {
    private val XDS_FACTORY_SIMPLE_NAMES =
        setOf(
            "XdsEndpointGroup",
            "XdsHttpPreprocessor",
            "XdsRpcPreprocessor",
        )

    internal val XDS_FACTORY_METHOD_NAMES = setOf("of", "ofListener")

    internal const val LISTENER_NAME_PARAMETER = "listenerName"

    private const val XDS_PACKAGE_PREFIX = "com.linecorp.armeria.xds."

    fun xdsKind(): String = message("client.explorer.endpointGroup.xds")

    fun isArmeriaXdsClass(qualifiedName: String?): Boolean =
        qualifiedName != null &&
            qualifiedName.startsWith(XDS_PACKAGE_PREFIX) &&
            qualifiedName.substringAfterLast('.') in XDS_FACTORY_SIMPLE_NAMES

    /** Receiver class FQN of a static xDS factory call, when resolvable. */
    fun resolveJavaFactoryClass(call: PsiMethodCallExpression): String? = call.resolveMethod()?.containingClass?.qualifiedName

    fun labelJavaXdsFactory(expression: PsiExpression?): String? = labelJavaXdsFactory(expression, mutableSetOf())

    internal fun labelJavaXdsFactory(
        expression: PsiExpression?,
        visited: MutableSet<PsiElement>,
    ): String? =
        when (expression) {
            null -> null
            is PsiMethodCallExpression -> {
                val methodName = expression.methodExpression.referenceName
                if (methodName !in XDS_FACTORY_METHOD_NAMES) {
                    return null
                }
                val method = expression.resolveMethod()
                if (method != null) {
                    if (!isArmeriaXdsClass(method.containingClass?.qualifiedName)) {
                        return null
                    }
                    return xdsLabel(extractJavaListenerName(expression, method))
                }
                val receiver = expression.methodExpression.qualifierExpression as? PsiReferenceExpression
                val receiverClass = receiver?.resolve() as? PsiClass ?: return null
                if (!isArmeriaXdsClass(receiverClass.qualifiedName)) {
                    return null
                }
                val fallback =
                    listenerNameMethod(receiverClass, methodName, expression.argumentList.expressions.size)
                xdsLabel(
                    fallback?.let { extractJavaListenerName(expression, it) }
                        ?: expression.argumentList.expressions.reversed().firstNotNullOfOrNull {
                            ArmeriaClientCollector.extractResolvedString(it)
                        },
                )
            }
            is PsiParenthesizedExpression -> labelJavaXdsFactory(expression.expression, visited)
            is PsiReferenceExpression -> {
                val resolved = expression.resolve() as? PsiVariable ?: return null
                if (!visited.add(resolved)) {
                    return null
                }
                labelJavaXdsFactory(resolved.initializer, visited)
            }
            else -> null
        }

    /** Prefer an overload whose arity matches the call so listenerName binds the right argument. */
    internal fun listenerNameMethod(
        containingClass: PsiClass,
        methodName: String?,
        argumentCount: Int,
    ): PsiMethod? {
        methodName ?: return null
        val candidates = containingClass.findMethodsByName(methodName, false).toList()
        val arityMatched = candidates.filter { it.parameterList.parametersCount == argumentCount }
        return (arityMatched.ifEmpty { candidates })
            .filter { candidate ->
                candidate.parameterList.parameters.any { it.name == LISTENER_NAME_PARAMETER }
            }.maxByOrNull { candidate ->
                candidate.parameterList.parameters.indexOfLast { it.name == LISTENER_NAME_PARAMETER }
            }
    }

    private fun extractJavaListenerName(
        call: PsiMethodCallExpression,
        method: PsiMethod,
    ): String? {
        val parameters = method.parameterList.parameters
        val arguments = call.argumentList.expressions
        val listenerIndex = parameters.indexOfFirst { it.name == LISTENER_NAME_PARAMETER }
        if (listenerIndex >= 0 && listenerIndex < arguments.size) {
            ArmeriaClientCollector.extractResolvedString(arguments[listenerIndex])?.let { return it }
        }
        return arguments.reversed().firstNotNullOfOrNull { ArmeriaClientCollector.extractResolvedString(it) }
    }

    internal fun xdsLabel(detail: String?): String = if (detail != null) "${xdsKind()} ($detail)" else xdsKind()
}
