package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression

internal object ArmeriaClientXdsSupport {
    private val XDS_FACTORY_SIMPLE_NAMES =
        setOf(
            "XdsEndpointGroup",
            "XdsHttpPreprocessor",
            "XdsRpcPreprocessor",
        )

    private val XDS_FACTORY_METHOD_NAMES = setOf("of", "ofListener")

    private const val LISTENER_NAME_PARAMETER = "listenerName"

    fun xdsKind(): String = message("client.explorer.endpointGroup.xds")

    fun isArmeriaXdsClass(qualifiedName: String?): Boolean =
        qualifiedName != null &&
            qualifiedName.startsWith("com.linecorp.armeria.") &&
            "xds" in qualifiedName &&
            qualifiedName.substringAfterLast('.') in XDS_FACTORY_SIMPLE_NAMES

    /** Receiver class FQN of a static xDS factory call, when resolvable. */
    fun resolveJavaFactoryClass(call: PsiMethodCallExpression): String? = call.resolveMethod()?.containingClass?.qualifiedName

    /** Receiver class FQN of a Kotlin static factory call, when resolvable. */
    fun resolveKotlinFactoryClass(call: KtCallExpression): String? = resolveKotlinFactoryMethod(call)?.containingClass?.qualifiedName

    private fun resolveKotlinFactoryMethod(call: KtCallExpression): PsiMethod? =
        call.calleeExpression
            ?.references
            ?.firstNotNullOfOrNull { it.resolve() as? PsiMethod }

    fun labelJavaXdsFactory(expression: PsiExpression?): String? =
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
                    receiverClass
                        .findMethodsByName(methodName, false)
                        .firstOrNull { candidate ->
                            candidate.parameterList.parameters.any { it.name == LISTENER_NAME_PARAMETER }
                        }
                xdsLabel(
                    fallback?.let { extractJavaListenerName(expression, it) }
                        ?: expression.argumentList.expressions.reversed().firstNotNullOfOrNull {
                            ArmeriaClientCollector.extractResolvedString(it)
                        },
                )
            }
            is PsiReferenceExpression -> {
                val resolved = expression.resolve() as? PsiVariable ?: return null
                labelJavaXdsFactory(resolved.initializer)
            }
            else -> null
        }

    fun labelKotlinXdsFactory(expression: KtExpression?): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val call = ArmeriaKotlinClientCollector.callExpressionInChain(unwrapped)
        if (call != null) {
            return labelKotlinXdsCall(call)
        }
        if (unwrapped is KtNameReferenceExpression) {
            return when (val resolved = unwrapped.references.firstOrNull()?.resolve()) {
                is KtProperty -> labelKotlinXdsFactory(resolved.initializer)
                is PsiVariable -> labelJavaXdsFactory(resolved.initializer)
                else -> null
            }
        }
        return null
    }

    private fun labelKotlinXdsCall(call: KtCallExpression): String? {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call)
        if (methodName !in XDS_FACTORY_METHOD_NAMES) {
            return null
        }
        val method = resolveKotlinFactoryMethod(call)
        if (method != null) {
            if (!isArmeriaXdsClass(method.containingClass?.qualifiedName)) {
                return null
            }
            return xdsLabel(extractKotlinListenerName(call, method))
        }
        val receiver = (call.parent as? KtQualifiedExpression)?.receiverExpression ?: return null
        val resolved = receiver.references.firstOrNull()?.resolve() ?: return null
        val qualifiedName =
            when (resolved) {
                is PsiClass -> resolved.qualifiedName
                is KtClassOrObject -> resolved.fqName?.asString()
                else -> null
            }
        if (!isArmeriaXdsClass(qualifiedName)) {
            return null
        }
        val fallback =
            (resolved as? PsiClass)
                ?.findMethodsByName(methodName, false)
                ?.firstOrNull { candidate ->
                    candidate.parameterList.parameters.any { it.name == LISTENER_NAME_PARAMETER }
                }
        return xdsLabel(
            fallback?.let { extractKotlinListenerName(call, it) }
                ?: call.valueArguments.asReversed().firstNotNullOfOrNull { argument ->
                    ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument.getArgumentExpression())
                },
        )
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

    private fun extractKotlinListenerName(
        call: KtCallExpression,
        method: PsiMethod,
    ): String? {
        val parameters = method.parameterList.parameters
        val listenerIndex = parameters.indexOfFirst { it.name == LISTENER_NAME_PARAMETER }
        if (listenerIndex >= 0) {
            val argument =
                ArmeriaKotlinExpressionSupport.findArgumentExpression(
                    call.valueArguments,
                    LISTENER_NAME_PARAMETER,
                    listenerIndex,
                )
            ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument)?.let { return it }
        }
        return call.valueArguments.asReversed().firstNotNullOfOrNull { argument ->
            ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument.getArgumentExpression())
        }
    }

    private fun xdsLabel(detail: String?): String = if (detail != null) "${xdsKind()} ($detail)" else xdsKind()
}
