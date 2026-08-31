package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable

internal object ArmeriaRouteTargetExtractor {
    private val BUILDER_CHAIN_METHODS = setOf("build", "addService", "addServices", "enableUnframedRequests")

    fun extractKnownServiceType(expression: PsiExpression): String? = extractKnownServiceType(expression, mutableSetOf())

    private fun extractKnownServiceType(
        expression: PsiExpression,
        visitedVariables: MutableSet<PsiVariable>,
    ): String? {
        val unwrapped = unwrapCast(expression) ?: return null
        return when (unwrapped) {
            is PsiNewExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                val resolved = unwrapped.classReference?.resolve() as? PsiClass
                val classReference =
                    resolved?.qualifiedName
                        ?: unwrapped.classReference?.qualifiedName
                        ?: unwrapped.classReference?.referenceName
                classReference?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
            }
            is PsiMethodCallExpression -> extractKnownServiceTypeFromCall(unwrapped, visitedVariables)
            is PsiReferenceExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                when (val resolved = unwrapped.resolve()) {
                    is PsiVariable -> extractKnownServiceTypeFromVariable(resolved, visitedVariables)
                    is PsiClass ->
                        resolved.qualifiedName?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
                            ?: resolved.name
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractKnownServiceTypeFromVariable(
        variable: PsiVariable,
        visitedVariables: MutableSet<PsiVariable>,
    ): String? {
        val declaredType = ArmeriaKnownHttpServiceClassifier.canonicalServiceTypeName(variable.type.canonicalText)
        ArmeriaKnownHttpServiceClassifier.knownServiceTypeNameOrNull(declaredType)?.let { return it }
        val initializer = variable.initializer ?: return declaredType
        if (!visitedVariables.add(variable)) {
            return declaredType
        }
        return extractKnownServiceType(initializer, visitedVariables)
            ?.let(ArmeriaKnownHttpServiceClassifier::knownServiceTypeNameOrNull)
            ?: declaredType
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

    private fun extractKnownServiceTypeFromCall(
        call: PsiMethodCallExpression,
        visitedVariables: MutableSet<PsiVariable>,
    ): String? {
        val methodName = call.methodExpression.referenceName
        val qualifier = call.methodExpression.qualifierExpression
        if (methodName in BUILDER_CHAIN_METHODS && qualifier != null) {
            ArmeriaKnownHttpServiceClassifier
                .knownServiceTypeNameOrNull(extractKnownServiceType(qualifier, visitedVariables))
                ?.let { return it }
        }
        if (methodName == "builder" || methodName == "of") {
            serviceTypeFromResolvedCall(call)?.let { return it }
            return if (qualifier != null) extractKnownServiceType(qualifier, visitedVariables) else null
        }
        serviceTypeFromResolvedCall(call)?.let { return it }
        return if (qualifier != null) extractKnownServiceType(qualifier, visitedVariables) else null
    }

    private fun serviceTypeFromResolvedCall(call: PsiMethodCallExpression): String? {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = call.resolveMethod()?.containingClass ?: return null
        val serviceClassName =
            resolvedClass.qualifiedName?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
                ?: resolvedClass.name?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
                ?: return null
        return serviceClassName.takeIf {
            ArmeriaKnownHttpServiceClassifier.classify(it) != KnownHttpServiceKind.HTTP
        }
    }

    private fun extractMethodCallTarget(
        call: PsiMethodCallExpression,
        fallbackExpression: PsiExpression,
    ): String {
        val methodName = call.methodExpression.referenceName
        if (methodName in BUILDER_CHAIN_METHODS) {
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
            resolvedClass?.qualifiedName?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
                ?: resolvedClass?.name?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
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
            builderClass.qualifiedName?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
                ?: builderClass.name?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
        return if (argumentTarget.isNotBlank()) {
            "$serviceName($argumentTarget)"
        } else {
            serviceName
        }
    }
}

fun extractArmeriaRouteTarget(expression: PsiExpression): String = ArmeriaRouteTargetExtractor.extractTarget(expression)
