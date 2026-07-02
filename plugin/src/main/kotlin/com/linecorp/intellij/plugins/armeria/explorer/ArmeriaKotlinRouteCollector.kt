package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaKotlinRouteCollector {
    private val IMPLICIT_RECEIVER_SCOPE_METHODS = setOf("apply", "run")
    private val EXPLICIT_PARAMETER_SCOPE_METHODS = setOf("also", "let")
    private val BUILDER_SCOPE_METHOD_NAMES = IMPLICIT_RECEIVER_SCOPE_METHODS + EXPLICIT_PARAMETER_SCOPE_METHODS

    internal fun referencesArmeriaKotlinContent(file: KtFile): Boolean {
        val hasArmeriaImports = file.importList?.imports?.any { import ->
            import.importedFqName?.asString()?.startsWith(ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX) == true
        } ?: false
        if (hasArmeriaImports) {
            return true
        }
        return ArmeriaRouteSupport.referencesArmeriaInText(file.viewProvider.contents)
    }

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
            if (!referencesArmeriaKotlinContent(ktFile)) {
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
        file.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
            val methodName = resolveCallName(call) ?: return@forEachDescendant
            if (methodName !in ServiceRegistrationMethod.METHOD_NAMES) {
                return@forEachDescendant
            }
            if (!looksLikeArmeriaBuilderCall(call)) {
                return@forEachDescendant
            }
            addKotlinServiceRegistration(call, methodName, routes, seenServiceRegistrations)
        }
    }

    private inline fun PsiElement.forEachDescendant(action: (PsiElement) -> Unit) {
        val queue = ArrayDeque<PsiElement>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()
            action(element)
            for (child in element.children) {
                queue.add(child)
            }
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
        val dotQualified = call.parent as? KtDotQualifiedExpression
        if (dotQualified != null) {
            for (reference in dotQualified.references) {
                if (isArmeriaServerBuilderMethod(reference.resolve())) {
                    return true
                }
            }
        }
        val callee = call.calleeExpression ?: return false
        val references = when (callee) {
            is KtNameReferenceExpression -> callee.references.toList()
            is KtDotQualifiedExpression -> callee.references.toList()
            else -> emptyList()
        }
        return references.any { isArmeriaServerBuilderMethod(it.resolve()) }
    }

    private fun isArmeriaServerBuilderMethod(resolved: PsiElement?): Boolean {
        return resolved is PsiMethod &&
            resolved.containingClass?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun isServerBuilderReceiver(receiver: KtExpression): Boolean {
        val receiverExpression = unwrapReceiverExpression(receiver)
        val receiverText = receiverExpression.text
        if (ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(receiverText)) {
            return true
        }
        if (receiverExpression is KtNameReferenceExpression) {
            when (val resolved = receiverExpression.references.firstOrNull()?.resolve()) {
                is PsiVariable -> {
                    if (ArmeriaRouteSupport.isServerBuilderType(resolved.type.canonicalText)) {
                        return true
                    }
                }
                is KtProperty -> {
                    val typeText = ArmeriaRouteSupport.resolveKotlinTypeReferenceText(resolved.typeReference)
                    if (typeText != null && ArmeriaRouteSupport.isServerBuilderType(typeText)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun unwrapReceiverExpression(receiver: KtExpression): KtExpression {
        return when (receiver) {
            is KtUnaryExpression -> receiver.baseExpression?.let(::unwrapReceiverExpression) ?: receiver
            else -> receiver
        }
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
        if (!isServerBuilderReceiver(scopeReceiver) && !receiverChainContainsServerBuilder(scopeReceiver)) {
            return false
        }
        return when (scopeMethod) {
            in IMPLICIT_RECEIVER_SCOPE_METHODS -> true
            in EXPLICIT_PARAMETER_SCOPE_METHODS -> isRegistrationOnScopeLambdaParameter(call, lambda)
            else -> false
        }
    }

    private fun isRegistrationOnScopeLambdaParameter(
        call: KtCallExpression,
        scopeLambda: KtLambdaExpression,
    ): Boolean {
        val dotQualified = when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee
            else -> call.parent as? KtDotQualifiedExpression
        } ?: return false

        val receiver = dotQualified.receiverExpression
        if (isServerBuilderReceiver(receiver)) {
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
        val virtualFile = call.containingKtFile.virtualFile ?: return
        val registrationKey = "${virtualFile.path}:${call.textRange.startOffset}"
        val arguments = call.valueArguments
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = resolveServiceExpression(methodName, arguments) ?: return
        val unwrappedImplementation = unwrapKotlinExpression(implementationExpression) ?: return
        val targetExpression = extractKotlinTargetExpression(unwrappedImplementation)
        val target = renderKotlinTarget(targetExpression)
        val targetUnresolved = isUnresolvedKotlinTarget(targetExpression, target)
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

    private fun findPathPrefixArgument(arguments: List<KtValueArgument>, positionalIndex: Int): KtExpression? {
        return findArgumentExpression(arguments, "pathPrefix", positionalIndex)
            ?: findArgumentExpression(arguments, "prefix", positionalIndex)
    }

    private fun extractRegistrationPath(methodName: String, arguments: List<KtValueArgument>): String? {
        return when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.SERVICE ->
                extractKotlinString(findArgumentExpression(arguments, "path", 0))
            ServiceRegistrationMethod.SERVICE_UNDER ->
                extractKotlinString(findPathPrefixArgument(arguments, 0))
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> {
                if (arguments.size > 1) {
                    extractKotlinString(findPathPrefixArgument(arguments, 0))
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
            is KtDotQualifiedExpression -> extractKotlinStringFromReference(unwrapped)
            is KtNameReferenceExpression -> extractKotlinStringFromReference(unwrapped)
            else -> unwrapped.text.trim('"').takeIf { it.isNotEmpty() }
        }
    }

    private fun extractKotlinStringFromReference(expression: KtExpression): String? {
        val resolved = expression.references.firstOrNull()?.resolve()
        when (resolved) {
            is KtProperty -> extractKotlinString(resolved.initializer)?.let { return it }
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
        return expression.text.trim('"').takeIf { it.isNotEmpty() }
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

    private fun extractKotlinTargetExpression(expression: KtExpression): KtExpression {
        if (expression is KtDotQualifiedExpression) {
            val selector = expression.selectorExpression
            if (selector is KtCallExpression) {
                return extractKotlinTargetExpression(selector)
            }
        }
        if (expression is KtCallExpression) {
            val callee = expression.calleeExpression
            val methodName = when (callee) {
                is KtDotQualifiedExpression -> callee.selectorExpression?.text
                else -> callee?.text
            }
            if (methodName == "build") {
                val receiver = dotQualifiedReceiver(callee, expression) ?: return expression
                return extractKotlinTargetExpression(receiver)
            }
            if (methodName == "builder") {
                expression.valueArguments.firstOrNull()?.getArgumentExpression()?.let { serviceArg ->
                    return extractKotlinTargetExpression(serviceArg)
                }
                val receiver = dotQualifiedReceiver(callee, expression)
                if (receiver is KtNameReferenceExpression) {
                    val resolved = receiver.references.firstOrNull()?.resolve()
                    if (isResolvedKotlinClass(resolved)) {
                        return receiver
                    }
                }
            }
        }
        return expression
    }

    private fun renderKotlinTarget(expression: KtExpression): String {
        if (expression is KtCallExpression) {
            val callee = expression.calleeExpression
            val reference = callee as? KtNameReferenceExpression ?: callee?.let {
                if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
            }
            val resolved = reference?.references?.firstOrNull()?.resolve()
            resolveQualifiedClassName(resolved)?.let { return it }
            return reference?.getReferencedName() ?: expression.text
        }
        if (expression is KtNameReferenceExpression) {
            val resolved = expression.references.firstOrNull()?.resolve()
            resolveQualifiedClassName(resolved)?.let { return it }
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
                if (methodName != null && extractedTarget == methodName) {
                    true
                } else {
                    val reference = callee as? KtNameReferenceExpression ?: callee?.let {
                        if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
                    }
                    val resolved = reference?.references?.firstOrNull()?.resolve()
                    when {
                        isResolvedKotlinClass(resolved) -> false
                        resolved is PsiMethod -> {
                            if (resolved.isConstructor) {
                                false
                            } else {
                                extractedTarget == methodName ||
                                    extractedTarget == resolved.containingClass?.qualifiedName
                            }
                        }
                        isResolvedKotlinConstructor(resolved) -> false
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

    private fun resolveQualifiedClassName(resolved: PsiElement?): String? {
        return when (resolved) {
            is com.intellij.psi.PsiClass -> resolved.qualifiedName
            is PsiMethod -> resolved.containingClass?.qualifiedName
            is KtClassOrObject -> resolved.fqName?.asString()
            is KtConstructor<*> -> resolved.getContainingClassOrObject().fqName?.asString()
            else -> null
        }
    }

    private fun isResolvedKotlinClass(resolved: PsiElement?): Boolean {
        return resolved is com.intellij.psi.PsiClass || resolved is KtClassOrObject
    }

    private fun isResolvedKotlinConstructor(resolved: PsiElement?): Boolean {
        return resolved is PsiMethod && resolved.isConstructor || resolved is KtConstructor<*>
    }
}
