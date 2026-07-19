package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol

internal object ArmeriaRouteTargetExtractor {
    fun detectProtocol(expressionText: String): RouteProtocol =
        when {
            expressionText.contains("GrpcService") -> RouteProtocol.GRPC
            expressionText.contains("DocService") -> RouteProtocol.DOC_SERVICE
            expressionText.contains("Thrift", ignoreCase = true) -> RouteProtocol.THRIFT
            else -> RouteProtocol.HTTP
        }

    fun isUnresolvedTarget(
        expression: PsiExpression,
        extractedTarget: String,
    ): Boolean {
        val rawTarget = expression.text.trim()
        val unwrapped = unwrapCast(expression) ?: return true
        return when (unwrapped) {
            is PsiNewExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                unwrapped.classReference?.resolve() == null
            }
            is PsiReferenceExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                unwrapped.resolve() == null
            }
            is PsiMethodCallExpression -> isUnresolvedMethodCallTarget(unwrapped, extractedTarget)
            else -> extractedTarget == rawTarget
        }
    }

    private fun isUnresolvedMethodCallTarget(
        call: PsiMethodCallExpression,
        extractedTarget: String,
    ): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedMethod = call.resolveMethod() ?: return true
        val methodName = call.methodExpression.referenceName
        if (methodName != null && extractedTarget == methodName) {
            return true
        }
        val declaringClass = resolvedMethod.containingClass?.qualifiedName
        return declaringClass != null && extractedTarget == declaringClass
    }

    fun extractTarget(expression: PsiExpression): String {
        val unwrapped = unwrapCast(expression) ?: return expression.text
        return when (unwrapped) {
            is PsiNewExpression -> {
                val classReference = unwrapped.classReference?.qualifiedName ?: unwrapped.classReference?.referenceName
                classReference ?: expression.text
            }

            is PsiMethodCallExpression -> extractMethodCallTarget(unwrapped, expression)
            is PsiReferenceExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                when (val resolved = unwrapped.resolve()) {
                    is PsiVariable -> resolved.type.presentableText
                    is PsiClass -> resolved.qualifiedName ?: resolved.name ?: expression.text
                    else -> unwrapped.text
                }
            }

            else -> expression.text
        }
    }

    private fun unwrapCast(expression: PsiExpression): PsiExpression? {
        var current: PsiExpression = expression
        while (true) {
            current =
                when (current) {
                    is PsiTypeCastExpression -> current.operand ?: return null
                    is PsiParenthesizedExpression -> current.expression ?: return null
                    else -> return current
                }
        }
    }

    private fun extractMethodCallTarget(
        call: PsiMethodCallExpression,
        fallbackExpression: PsiExpression,
    ): String {
        val methodName = call.methodExpression.referenceName
        if (methodName == "build") {
            val qualifier = call.methodExpression.qualifierExpression
            if (qualifier != null) {
                return extractTarget(qualifier)
            }
        }
        if (methodName == "builder") {
            extractBuilderSeed(call)?.let { return it }
        }
        val qualifier = call.methodExpression.qualifierExpression
        if (qualifier != null) {
            val fromQualifier = extractTarget(qualifier)
            if (fromQualifier != methodName && fromQualifier != "build" && fromQualifier != qualifier.text) {
                return fromQualifier
            }
        }
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = call.resolveMethod()?.containingClass
        val serviceClassName =
            resolvedClass?.qualifiedName?.let(::builderTypeToServiceName)
                ?: resolvedClass?.name?.let(::builderTypeToServiceName)
        if (serviceClassName != null) {
            return serviceClassName
        }
        return methodName ?: fallbackExpression.text
    }

    private fun extractBuilderSeed(builderCall: PsiMethodCallExpression): String? {
        val firstArgument = builderCall.argumentList.expressions.firstOrNull() ?: return null
        val argumentTarget = extractTarget(firstArgument)
        if (argumentTarget.isNotBlank() && argumentTarget != firstArgument.text) {
            return argumentTarget
        }
        val builderClass = builderCall.resolveMethod()?.containingClass ?: return null
        val serviceName =
            builderClass.qualifiedName?.let(::builderTypeToServiceName)
                ?: builderClass.name?.let(::builderTypeToServiceName)
        return if (argumentTarget.isNotBlank()) {
            "$serviceName($argumentTarget)"
        } else {
            serviceName
        }
    }

    private fun builderTypeToServiceName(qualifiedOrSimpleName: String): String {
        val simpleName = qualifiedOrSimpleName.substringAfterLast('.')
        if (!simpleName.endsWith("Builder")) {
            return qualifiedOrSimpleName
        }
        val serviceSimpleName = simpleName.removeSuffix("Builder")
        val packagePrefix = qualifiedOrSimpleName.substringBeforeLast('.', missingDelimiterValue = "")
        return if (packagePrefix.isEmpty()) {
            serviceSimpleName
        } else {
            "$packagePrefix.$serviceSimpleName"
        }
    }
}

fun extractArmeriaRouteTarget(expression: PsiExpression): String = ArmeriaRouteTargetExtractor.extractTarget(expression)
