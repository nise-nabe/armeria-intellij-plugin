package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinDecoratorSupport {
    fun collectProgrammaticDecorators(element: PsiElement, registrationPath: String): List<String> {
        val registrationCall = element as? KtCallExpression ?: return emptyList()
        return collectKotlinDecoratorsOnBuilderChain(registrationCall, registrationPath)
    }

    private fun collectKotlinDecoratorsOnBuilderChain(
        registrationCall: KtCallExpression,
        registrationPath: String,
    ): List<String> {
        val candidates = linkedSetOf<ArmeriaDecoratorSupport.DecoratorCandidate>()
        collectKotlinDecoratorsFromReceiver(registrationCall, candidates)
        collectKotlinDecoratorsFromPrecedingScopeBlocks(registrationCall, candidates)
        collectKotlinDecoratorsInScopeLambda(registrationCall, candidates)
        enclosingBuilderScopeCall(registrationCall)?.let { scopeCall ->
            collectKotlinDecoratorsFromPrecedingScopeBlocks(scopeCall, candidates)
        }
        return ArmeriaDecoratorSupport.filterDecoratorCandidates(candidates, registrationPath)
    }

    private fun collectKotlinDecoratorsFromReceiver(
        expression: KtExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        var current: KtExpression? = kotlinChainReceiver(expression)
        while (current != null) {
            val decoratorCall = asKotlinCallExpression(current)
            if (decoratorCall != null && isKotlinArmeriaDecoratorCall(decoratorCall)) {
                extractKotlinDecoratorCandidate(decoratorCall)?.let { candidates += it }
            }
            current = kotlinChainReceiver(current)
        }
    }

    private fun collectKotlinDecoratorsFromPrecedingScopeBlocks(
        registrationCall: KtCallExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        var current: KtExpression? = kotlinChainReceiver(registrationCall)
        while (current != null) {
            val scopeCall = asKotlinCallExpression(current)
            if (scopeCall != null && resolveKotlinCallName(scopeCall) in BUILDER_SCOPE_METHODS) {
                val scopeReceiver = extractScopeReceiver(scopeCall)
                if (scopeReceiver != null &&
                    (isKotlinServerBuilderReceiver(scopeReceiver) || receiverChainContainsServerBuilder(scopeReceiver))
                ) {
                    collectAllDecoratorsInScopeLambda(scopeCall, candidates)
                }
            }
            current = kotlinChainReceiver(current)
        }
    }

    private fun collectAllDecoratorsInScopeLambda(
        scopeCall: KtCallExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        val lambda = scopeCall.valueArguments.firstOrNull()
            ?.getArgumentExpression() as? KtLambdaExpression ?: return
        lambda.bodyExpression?.statements?.forEach { statement ->
            statement.accept(object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (isKotlinArmeriaDecoratorCall(expression)) {
                        extractKotlinDecoratorCandidate(expression)?.let { candidates += it }
                    }
                    super.visitCallExpression(expression)
                }
            })
        }
    }

    private fun asKotlinCallExpression(expression: KtExpression): KtCallExpression? {
        return when (expression) {
            is KtCallExpression -> expression
            is KtDotQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
    }

    private fun kotlinChainReceiver(expression: KtExpression): KtExpression? {
        return when (expression) {
            is KtDotQualifiedExpression -> expression.receiverExpression
            is KtCallExpression -> {
                val parent = expression.parent
                if (parent is KtDotQualifiedExpression && parent.selectorExpression == expression) {
                    parent.receiverExpression
                } else {
                    when (val callee = expression.calleeExpression) {
                        is KtDotQualifiedExpression -> callee.receiverExpression
                        else -> null
                    }
                }
            }
            else -> null
        }
    }

    private fun isKotlinArmeriaDecoratorCall(call: KtCallExpression): Boolean {
        if (resolveKotlinCallName(call) != "decorator") {
            return false
        }
        val dotQualifiedParent = call.parent as? KtDotQualifiedExpression
        if (dotQualifiedParent != null && isKotlinServerBuilderReceiver(dotQualifiedParent.receiverExpression)) {
            return true
        }
        if (hasScopeBuilderReceiver(call)) {
            return true
        }
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val callee = call.calleeExpression
        val references = when (callee) {
            is KtNameReferenceExpression -> callee.references.toList()
            is KtDotQualifiedExpression -> callee.references.toList()
            else -> emptyList()
        }
        if (references.any { reference ->
                val resolved = reference.resolve()
                resolved is PsiMethod &&
                    resolved.containingClass?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
            }) {
            return true
        }
        val receiver = when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> dotQualifiedParent?.receiverExpression
        }
        return receiver != null && isKotlinServerBuilderReceiver(receiver)
    }

    private fun isKotlinServerBuilderReceiver(receiver: KtExpression): Boolean {
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(receiver.text)) {
            return true
        }
        if (receiver is KtNameReferenceExpression) {
            when (val resolved = receiver.references.firstOrNull()?.resolve()) {
                is PsiVariable -> {
                    if (ArmeriaRouteSupport.isServerBuilderType(resolved.type.canonicalText)) {
                        return true
                    }
                }
                is KtProperty -> {
                    val typeText = resolveKotlinTypeReferenceText(resolved.typeReference)
                    if (typeText != null && ArmeriaRouteSupport.isServerBuilderType(typeText)) {
                        return true
                    }
                    val initializerText = resolved.initializer?.text
                    if (initializerText != null &&
                        ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(initializerText)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun resolveKotlinTypeReferenceText(typeReference: KtTypeReference?): String? {
        if (typeReference == null) {
            return null
        }
        val userType = typeReference.typeElement as? KtUserType
        val resolved = userType?.referenceExpression?.references?.firstOrNull()?.resolve()
        return when (resolved) {
            is KtTypeAlias -> resolved.getTypeReference()?.text ?: typeReference.text
            is KtClass -> resolved.fqName?.asString() ?: typeReference.text
            else -> typeReference.text
        }
    }

    private fun extractKotlinDecoratorCandidate(call: KtCallExpression): ArmeriaDecoratorSupport.DecoratorCandidate? {
        val arguments = call.valueArguments
        val pathPattern = if (arguments.size >= 2) {
            extractKotlinPathPattern(arguments[0].getArgumentExpression())
        } else {
            null
        }
        val decoratorArgument = when {
            arguments.size >= 2 -> arguments[1].getArgumentExpression()
            arguments.isNotEmpty() -> arguments[0].getArgumentExpression()
            else -> return null
        } ?: return null
        return ArmeriaDecoratorSupport.DecoratorCandidate(
            ArmeriaDecoratorSupport.labelDecorator(extractKotlinDecoratorTarget(decoratorArgument)),
            pathPattern,
        )
    }

    private fun extractKotlinDecoratorTarget(expression: KtExpression): String {
        val unwrapped = unwrapKotlinDecoratorExpression(expression) ?: return expression.text
        if (unwrapped is KtClassLiteralExpression) {
            val classReceiver = unwrapped.receiverExpression
            if (classReceiver is KtNameReferenceExpression) {
                resolveQualifiedClassName(classReceiver.references.firstOrNull()?.resolve())?.let { return it }
            }
            return unwrapped.text.removeSuffix("::class")
        }
        if (unwrapped is KtDotQualifiedExpression) {
            val receiver = unwrapped.receiverExpression
            if (receiver is KtClassLiteralExpression) {
                val classReceiver = receiver.receiverExpression
                if (classReceiver is KtNameReferenceExpression) {
                    resolveQualifiedClassName(classReceiver.references.firstOrNull()?.resolve())?.let { return it }
                }
                return receiver.text.removeSuffix("::class")
            }
            val selector = unwrapped.selectorExpression
            if (selector is KtCallExpression) {
                return extractKotlinDecoratorTargetFromCall(selector, expression)
            }
            resolveQualifiedClassName(receiver.references.firstOrNull()?.resolve())?.let { return it }
        }
        if (unwrapped is KtCallExpression) {
            return extractKotlinDecoratorTargetFromCall(unwrapped, expression)
        }
        if (unwrapped is KtNameReferenceExpression) {
            when (val resolved = unwrapped.references.firstOrNull()?.resolve()) {
                is KtProperty -> {
                    resolved.initializer?.let { return extractKotlinDecoratorTarget(it) }
                    resolveKotlinTypeReferenceText(resolved.typeReference)?.let { return it }
                }
                is PsiVariable -> {
                    resolved.initializer?.let { initializer ->
                        if (initializer is KtExpression) {
                            return extractKotlinDecoratorTarget(initializer)
                        }
                        return ArmeriaRouteTargetExtractor.extractTarget(initializer)
                    }
                    return resolved.type.presentableText
                }
            }
            resolveQualifiedClassName(unwrapped.references.firstOrNull()?.resolve())?.let { return it }
        }
        return expression.text
    }

    private fun extractKotlinDecoratorTargetFromCall(call: KtCallExpression, fallback: KtExpression): String {
        val callee = call.calleeExpression
        val methodName = when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee?.text
        }
        if (methodName == "build") {
            kotlinChainReceiver(call)?.let { receiver ->
                return extractKotlinDecoratorTarget(receiver)
            }
        }
        if (methodName == "builder" || methodName == "newDecorator") {
            kotlinChainReceiver(call)?.let { receiver ->
                val fromReceiver = extractKotlinDecoratorTarget(receiver)
                if (fromReceiver != methodName && fromReceiver != receiver.text) {
                    return fromReceiver
                }
            }
            ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
            val references = when (callee) {
                is KtDotQualifiedExpression -> callee.references.toList()
                is KtNameReferenceExpression -> callee.references.toList()
                else -> emptyList()
            }
            references.firstOrNull()?.resolve()?.let { resolved ->
                resolveQualifiedClassName(resolved)?.let { return it }
            }
        }
        kotlinChainReceiver(call)?.let { receiver ->
            val fromReceiver = extractKotlinDecoratorTarget(receiver)
            if (fromReceiver != methodName && fromReceiver != "build" && fromReceiver != receiver.text) {
                return fromReceiver
            }
        }
        return methodName ?: fallback.text
    }

    private fun unwrapKotlinDecoratorExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is KtParenthesizedExpression -> current.expression ?: return null
                else -> return current
            }
        }
    }

    private fun resolveQualifiedClassName(resolved: PsiElement?): String? {
        return when (resolved) {
            is com.intellij.psi.PsiClass -> resolved.qualifiedName
            is PsiMethod -> resolved.containingClass?.qualifiedName
            is KtClass -> resolved.fqName?.asString()
            else -> null
        }
    }

    private fun extractKotlinPathPattern(expression: KtExpression?): String? {
        val unwrapped = expression ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> {
                if (unwrapped.entries.size == 1) {
                    unwrapped.entries[0].text.trim('"')
                } else {
                    unwrapped.text.trim('"')
                }
            }
            is KtDotQualifiedExpression -> extractKotlinPathPatternFromReference(unwrapped)
            is KtNameReferenceExpression -> extractKotlinPathPatternFromReference(unwrapped)
            else -> unwrapped.text.trim().trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinPathPatternFromReference(expression: KtExpression): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> extractKotlinPathPattern(resolved.initializer)?.let { return it }
            is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)?.let { return it }
        }
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return null
            val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return null
            val containingClass = receiver.references.firstOrNull()?.resolve() as? com.intellij.psi.PsiClass
                ?: return null
            val field = containingClass.findFieldByName(selector.getReferencedName(), true)
            if (field != null) {
                ArmeriaRouteSupport.evaluateJavaStringConstant(field)?.let { return it }
            }
        }
        return expression.text.trim().trim('"').takeIf { it.isNotEmpty() }
    }

    private fun enclosingBuilderScopeCall(registrationCall: KtCallExpression): KtCallExpression? {
        val lambda = registrationCall.getParentOfType<KtLambdaExpression>(strict = true) ?: return null
        val scopeCall = (lambda.parent as? KtValueArgument)?.parent as? KtCallExpression ?: return null
        val scopeMethod = resolveKotlinCallName(scopeCall) ?: return null
        return scopeCall.takeIf { scopeMethod in BUILDER_SCOPE_METHODS }
    }

    private fun hasScopeBuilderReceiver(call: KtCallExpression): Boolean {
        val lambda = call.getParentOfType<KtLambdaExpression>(strict = true) ?: return false
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return false
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return false
        val scopeMethod = resolveKotlinCallName(scopeCall) ?: return false
        if (scopeMethod !in BUILDER_SCOPE_METHODS) {
            return false
        }
        val scopeReceiver = extractScopeReceiver(scopeCall) ?: return false
        if (!isKotlinServerBuilderReceiver(scopeReceiver) && !receiverChainContainsServerBuilder(scopeReceiver)) {
            return false
        }
        return when (scopeMethod) {
            in IMPLICIT_RECEIVER_SCOPE_METHODS -> true
            in EXPLICIT_PARAMETER_SCOPE_METHODS -> isCallOnScopeLambdaParameter(call, lambda)
            else -> false
        }
    }

    private fun isCallOnScopeLambdaParameter(
        call: KtCallExpression,
        scopeLambda: KtLambdaExpression,
    ): Boolean {
        val dotQualified = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee
            else -> call.parent as? KtDotQualifiedExpression
        } ?: return false

        val receiver = dotQualified.receiverExpression
        if (isKotlinServerBuilderReceiver(receiver)) {
            return true
        }
        val receiverName = (receiver as? KtNameReferenceExpression)?.getReferencedName() ?: return false
        if (scopeLambda.valueParameters.any { it.name == receiverName }) {
            return true
        }
        return scopeLambda.valueParameters.isEmpty() && receiverName == "it"
    }

    private fun receiverChainContainsServerBuilder(receiver: KtExpression): Boolean {
        var current: KtExpression? = receiver
        while (current != null) {
            if (isKotlinServerBuilderReceiver(current)) {
                return true
            }
            current = kotlinChainReceiver(current)
        }
        return false
    }

    private fun extractScopeReceiver(scopeCall: KtCallExpression): KtExpression? {
        return when (val callee = scopeCall.calleeExpression) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (scopeCall.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
    }

    private fun collectKotlinDecoratorsInScopeLambda(
        registrationCall: KtCallExpression,
        candidates: LinkedHashSet<ArmeriaDecoratorSupport.DecoratorCandidate>,
    ) {
        val lambda = registrationCall.getParentOfType<KtLambdaExpression>(strict = true) ?: return
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return
        val scopeMethod = resolveKotlinCallName(scopeCall) ?: return
        if (scopeMethod !in BUILDER_SCOPE_METHODS) {
            return
        }
        val registrationOffset = registrationCall.textRange.startOffset
        lambda.bodyExpression?.statements?.forEach { statement ->
            if (statement.textRange.startOffset >= registrationOffset) {
                return
            }
            statement.accept(object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (expression.textRange.startOffset >= registrationOffset) {
                        return
                    }
                    if (isKotlinArmeriaDecoratorCall(expression)) {
                        extractKotlinDecoratorCandidate(expression)?.let { candidates += it }
                    }
                    super.visitCallExpression(expression)
                }
            })
            if (PsiTreeUtil.isAncestor(statement, registrationCall, false)) {
                return
            }
        }
    }

    private fun resolveKotlinCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private val IMPLICIT_RECEIVER_SCOPE_METHODS = setOf("apply", "run")
    private val EXPLICIT_PARAMETER_SCOPE_METHODS = setOf("also", "let")
    private val BUILDER_SCOPE_METHODS = IMPLICIT_RECEIVER_SCOPE_METHODS + EXPLICIT_PARAMETER_SCOPE_METHODS
}
