package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinRouteCollector {
    private val BUILDER_SCOPE_METHOD_NAMES = setOf("apply", "run", "also", "let")

    fun collectServiceRegistrationsFallback(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!ArmeriaRouteCollector.referencesArmeriaContent(ktFile)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectServiceRegistrationsFromFile(ktFile, routes, seenServiceRegistrations)
        }
    }

    private fun collectServiceRegistrationsFromFile(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (call in file.collectDescendantsOfType<KtCallExpression>()) {
            ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
            val methodName = resolveCallName(call) ?: continue
            if (methodName !in ServiceRegistrationMethod.METHOD_NAMES) {
                continue
            }
            if (!looksLikeArmeriaBuilderCall(call)) {
                continue
            }
            addKotlinServiceRegistration(call, methodName, routes, seenServiceRegistrations)
        }
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    private fun looksLikeArmeriaBuilderCall(call: KtCallExpression): Boolean {
        if (resolvesToArmeriaServerBuilder(call)) {
            return true
        }
        val dotQualified = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee
            else -> call.parent as? KtDotQualifiedExpression
        }
        if (dotQualified != null && isServerBuilderReceiver(dotQualified.receiverExpression)) {
            return true
        }
        return hasServerBuilderImplicitReceiver(call)
    }

    private fun resolvesToArmeriaServerBuilder(call: KtCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val dotQualified = call.parent as? KtDotQualifiedExpression ?: return false
        for (reference in dotQualified.references) {
            val resolved = reference.resolve()
            if (resolved is PsiMethod &&
                resolved.containingClass?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
            ) {
                return true
            }
        }
        return false
    }

    private fun isServerBuilderReceiver(receiver: KtExpression): Boolean {
        val receiverText = receiver.text
        if (receiverText.contains("Server.builder()") || receiverText.contains("serverBuilder")) {
            return true
        }
        if (receiver is KtNameReferenceExpression) {
            when (val resolved = receiver.references.firstOrNull()?.resolve()) {
                is com.intellij.psi.PsiVariable -> {
                    if (ArmeriaRouteSupport.isServerBuilderType(resolved.type.canonicalText)) {
                        return true
                    }
                }
                is KtProperty -> {
                    val typeText = resolved.typeReference?.text
                    if (typeText != null && ArmeriaRouteSupport.isServerBuilderType(typeText)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasServerBuilderImplicitReceiver(call: KtCallExpression): Boolean {
        val lambda = call.getParentOfType<KtLambdaExpression>(strict = true) ?: return false
        val lambdaArgument = lambda.parent as? KtValueArgument ?: return false
        val scopeCall = lambdaArgument.parent as? KtCallExpression ?: return false
        val scopeMethod = resolveCallName(scopeCall) ?: return false
        if (scopeMethod !in BUILDER_SCOPE_METHOD_NAMES) {
            return false
        }
        val scopeReceiver = when (val callee = scopeCall.calleeExpression) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (scopeCall.parent as? KtDotQualifiedExpression)?.receiverExpression
        } ?: return false
        return isServerBuilderReceiver(scopeReceiver) ||
            receiverChainContainsServerBuilder(scopeReceiver)
    }

    private fun receiverChainContainsServerBuilder(receiver: KtExpression): Boolean {
        var current: KtExpression? = receiver
        while (current != null) {
            if (isServerBuilderReceiver(current)) {
                return true
            }
            current = when (current) {
                is KtDotQualifiedExpression -> current.receiverExpression
                is KtCallExpression -> {
                    when (val callee = current.calleeExpression) {
                        is KtDotQualifiedExpression -> callee.receiverExpression
                        else -> (current.parent as? KtDotQualifiedExpression)?.receiverExpression
                    }
                }
                else -> null
            }
        }
        return false
    }

    private fun addKotlinServiceRegistration(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val registrationKey = "${call.containingKtFile.virtualFile.path}:${call.textRange.startOffset}"
        val arguments = call.valueArguments
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = resolveServiceExpression(methodName, arguments) ?: return
        val unwrappedImplementation = unwrapKotlinExpression(implementationExpression) ?: return
        val target = extractKotlinTarget(unwrappedImplementation)
        val targetUnresolved = isUnresolvedKotlinTarget(implementationExpression, target)
        ArmeriaRouteCollector.addServiceRegistrationRoute(
            element = call,
            registrationKey = registrationKey,
            methodName = methodName,
            path = path,
            target = target,
            targetUnresolved = targetUnresolved,
            implementationText = implementationExpression.text,
            argumentCount = arguments.size,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
        )
    }

    private fun resolveServiceExpression(methodName: String, arguments: List<KtValueArgument>): KtExpression? {
        return when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.ANNOTATED_SERVICE ->
                findArgumentExpression(arguments, "service", 1)
                    ?: findArgumentExpression(arguments, "service", 0)
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER ->
                findArgumentExpression(arguments, "service", 1)
            null -> null
        }
    }

    private fun findArgumentExpression(
        arguments: List<KtValueArgument>,
        parameterName: String,
        positionalIndex: Int,
    ): KtExpression? {
        arguments.firstOrNull { argument ->
            argument.getArgumentName()?.asName?.identifier == parameterName
        }?.getArgumentExpression()?.let { return it }
        return arguments.getOrNull(positionalIndex)?.getArgumentExpression()
    }

    private fun extractRegistrationPath(methodName: String, arguments: List<KtValueArgument>): String? {
        return when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.SERVICE ->
                extractKotlinString(findArgumentExpression(arguments, "path", 0))
            ServiceRegistrationMethod.SERVICE_UNDER ->
                extractKotlinString(findArgumentExpression(arguments, "prefix", 0))
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> {
                if (arguments.size > 1) {
                    extractKotlinString(findArgumentExpression(arguments, "prefix", 0))
                } else {
                    "/"
                }
            }
            null -> null
        }
    }

    private fun extractKotlinString(expression: KtExpression?): String? {
        val unwrapped = unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtStringTemplateExpression -> {
                if (unwrapped.entries.size == 1) {
                    unwrapped.entries[0].text.trim('"')
                } else {
                    unwrapped.text.trim('"')
                }
            }
            is KtNameReferenceExpression -> {
                val resolved = unwrapped.references.firstOrNull()?.resolve()
                if (resolved is KtProperty) {
                    extractKotlinString(resolved.initializer)?.let { return it }
                }
                unwrapped.text.trim('"').takeIf { it.isNotEmpty() }
            }
            else -> unwrapped.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun unwrapKotlinExpression(expression: KtExpression?): KtExpression? {
        var current = expression ?: return null
        while (true) {
            current = when (current) {
                is KtParenthesizedExpression -> current.expression ?: return null
                else -> return current
            }
        }
    }

    private fun extractKotlinTarget(expression: KtExpression): String {
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression
            if (selector is KtCallExpression) {
                return extractKotlinTarget(selector)
            }
        }
        if (expression is KtCallExpression) {
            val callee = expression.calleeExpression
            val methodName = when (callee) {
                is KtDotQualifiedExpression -> callee.selectorExpression?.text
                else -> callee?.text
            }
            if (methodName == "build") {
                val receiver = dotQualifiedReceiver(callee, expression) ?: return expression.text
                return extractKotlinTarget(receiver)
            }
            if (methodName == "builder") {
                expression.valueArguments.firstOrNull()?.getArgumentExpression()?.let { serviceArg ->
                    return extractKotlinTarget(serviceArg)
                }
                val receiver = dotQualifiedReceiver(callee, expression)
                if (receiver is KtNameReferenceExpression) {
                    val resolved = receiver.references.firstOrNull()?.resolve()
                    if (resolved is com.intellij.psi.PsiClass) {
                        return resolved.qualifiedName ?: receiver.text
                    }
                }
            }
            val reference = callee as? KtNameReferenceExpression ?: callee?.let {
                if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
            }
            val resolved = reference?.references?.firstOrNull()?.resolve()
            val qualifiedName = when (resolved) {
                is com.intellij.psi.PsiClass -> resolved.qualifiedName
                is PsiMethod -> resolved.containingClass?.qualifiedName
                else -> null
            }
            if (qualifiedName != null) {
                return qualifiedName
            }
            return reference?.getReferencedName() ?: expression.text
        }
        if (expression is KtNameReferenceExpression) {
            val resolved = expression.references.firstOrNull()?.resolve()
            when (resolved) {
                is com.intellij.psi.PsiClass -> return resolved.qualifiedName ?: expression.text
            }
            return expression.text
        }
        return expression.text
    }

    private fun isUnresolvedKotlinTarget(expression: KtExpression, extractedTarget: String): Boolean {
        val unwrapped = unwrapKotlinExpression(expression) ?: return true
        val rawTarget = expression.text.trim()
        return when (unwrapped) {
            is KtCallExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                val callee = unwrapped.calleeExpression
                val methodName = when (callee) {
                    is KtDotQualifiedExpression -> callee.selectorExpression?.text
                    else -> callee?.text
                }
                if (methodName == "build" || methodName == "builder") {
                    false
                } else if (methodName != null && extractedTarget == methodName) {
                    true
                } else {
                    val reference = callee as? KtNameReferenceExpression ?: callee?.let {
                        if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
                    }
                    val resolved = reference?.references?.firstOrNull()?.resolve()
                    when (resolved) {
                        is com.intellij.psi.PsiClass -> false
                        is PsiMethod -> extractedTarget == methodName || extractedTarget == resolved.containingClass?.qualifiedName
                        else -> extractedTarget == rawTarget
                    }
                }
            }
            is KtNameReferenceExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                unwrapped.references.firstOrNull()?.resolve() == null
            }
            else -> extractedTarget == rawTarget
        }
    }

    private fun dotQualifiedReceiver(callee: org.jetbrains.kotlin.psi.KtExpression?, expression: KtCallExpression): KtExpression? {
        return when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (expression.parent as? KtDotQualifiedExpression)?.receiverExpression
        }
    }
}
