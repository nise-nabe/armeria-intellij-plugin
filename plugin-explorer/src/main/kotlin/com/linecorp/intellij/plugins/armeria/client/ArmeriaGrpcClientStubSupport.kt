package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiUtil

internal object ArmeriaGrpcClientStubSupport {
    fun isJavaGrpcStubBuild(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "build") {
            return false
        }
        if (call.argumentList.expressions.isEmpty()) {
            return false
        }
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (ArmeriaClientSupport.isGrpcClientBuilderClass(resolvedClass)) {
            return true
        }
        val qualifierText =
            call.methodExpression.qualifierExpression
                ?.text
                .orEmpty()
        return qualifierText.contains("GrpcClients") || qualifierText.contains("GrpcClientBuilder")
    }

    fun isJavaQualifierOfGrpcStubBuild(expression: PsiMethodCallExpression): Boolean {
        val parent = ArmeriaClientCollector.findEnclosingQualifierCall(expression) ?: return false
        return isJavaGrpcStubBuild(parent)
    }

    fun extractJavaStubClassName(call: PsiMethodCallExpression): String? =
        call.argumentList.expressions.firstNotNullOfOrNull(::extractJavaClassName)

    fun extractJavaClassName(expression: PsiExpression?): String? {
        val unwrapped = PsiUtil.skipParenthesizedExprDown(expression) ?: return null
        if (unwrapped is PsiClassObjectAccessExpression) {
            return classNameFromType(unwrapped.operand.type)
                ?: unwrapped.operand.innermostComponentReferenceElement?.qualifiedName
                ?: unwrapped.operand.innermostComponentReferenceElement?.referenceName
        }
        val text = unwrapped.text.trim()
        if (text.endsWith(".class")) {
            return text.removeSuffix(".class").takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun extractJavaBuilderUri(call: PsiMethodCallExpression): String? {
        var current: PsiExpression? = call
        while (current is PsiMethodCallExpression) {
            val methodName = current.methodExpression.referenceName
            if (methodName in ArmeriaClientSupport.FACTORY_METHOD_NAMES) {
                val uri = ArmeriaClientCollector.extractResolvedString(current.argumentList.expressions.firstOrNull())
                if (uri != null) {
                    return uri
                }
            }
            current = current.methodExpression.qualifierExpression
        }
        return ArmeriaClientCollector.extractResolvedString(call.argumentList.expressions.firstOrNull())
    }

    private fun classNameFromType(type: PsiType?): String? {
        val classType = type as? PsiClassType ?: return null
        val resolved = classType.resolve()
        return resolved?.qualifiedName ?: classType.className
    }
}
