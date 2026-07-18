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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

object ArmeriaKotlinRouteCollector {

    fun referencesArmeriaKotlinContent(file: KtFile): Boolean {
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

    internal fun collectServiceRegistrationsInScope(
        root: PsiElement,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        root.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
            val methodName = resolveCallName(call) ?: return@forEachDescendant
            if (methodName !in CoreServiceRegistrationMethod.METHOD_NAMES) {
                return@forEachDescendant
            }
            if (!ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)) {
                return@forEachDescendant
            }
            addKotlinServiceRegistration(call, methodName, routes, seenServiceRegistrations)
        }
    }

    private fun collectServiceRegistrationsFromFile(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        collectServiceRegistrationsInScope(file, routes, seenServiceRegistrations)
        ArmeriaKotlinExtendedRegistrationCollector.collectFromFile(file, routes, seenServiceRegistrations)
    }

    private fun resolveCallName(call: KtCallExpression): String? {
        val callee = call.calleeExpression ?: return null
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            else -> callee.text
        }
    }

    fun looksLikeArmeriaBuilderCall(call: KtCallExpression): Boolean =
        ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)

    internal fun addServiceRegistrationFromCall(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ): Boolean {
        val methodName = resolveCallName(call) ?: return false
        if (methodName !in CoreServiceRegistrationMethod.METHOD_NAMES) {
            return false
        }
        val sizeBefore = routes.size
        addKotlinServiceRegistration(call, methodName, routes, seenServiceRegistrations)
        return routes.size > sizeBefore
    }

    private fun addKotlinServiceRegistration(
        call: KtCallExpression,
        methodName: String,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val virtualFile = call.containingKtFile.virtualFile ?: return
        val registrationKey = ArmeriaRouteSupport.registrationKey(
            virtualFile.path,
            call.textRange,
            methodName,
        )
        val arguments = call.valueArguments
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = resolveServiceExpression(methodName, arguments) ?: return
        val unwrappedImplementation = unwrapKotlinExpression(implementationExpression) ?: return
        val targetExpression = extractKotlinTargetExpression(unwrappedImplementation)
        val target = renderKotlinTarget(targetExpression)
        val targetUnresolved = isUnresolvedKotlinTarget(targetExpression, target)
        ArmeriaRouteCollectorServiceRegistration.addServiceRegistrationRoute(
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
        return when (CoreServiceRegistrationMethod.fromMethodName(methodName)) {
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE ->
                findArgumentExpression(arguments, "service", 1)
                    ?: findArgumentExpression(arguments, "service", 0)
            CoreServiceRegistrationMethod.SERVICE, CoreServiceRegistrationMethod.SERVICE_UNDER ->
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
        return when (CoreServiceRegistrationMethod.fromMethodName(methodName)) {
            CoreServiceRegistrationMethod.SERVICE ->
                extractKotlinString(findArgumentExpression(arguments, "path", 0))
            CoreServiceRegistrationMethod.SERVICE_UNDER ->
                extractKotlinString(findPathPrefixArgument(arguments, 0))
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE -> {
                if (arguments.size > 1) {
                    extractKotlinString(findPathPrefixArgument(arguments, 0))
                } else {
                    "/"
                }
            }
            null -> null
        }
    }

    private fun extractKotlinString(expression: KtExpression?): String? =
        ArmeriaKotlinExpressionSupport.extractKotlinString(expression)

    fun extractKotlinStrings(expression: KtExpression?): List<String> {
        val unwrapped = unwrapKotlinExpression(expression) ?: return emptyList()
        if (unwrapped is KtCollectionLiteralExpression) {
            return unwrapped.getInnerExpressions().mapNotNull(::extractKotlinString)
        }
        return extractKotlinString(unwrapped)?.let { listOf(it) }.orEmpty()
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
            // Resolve bean/parameter/property types so servlet mounts match Java (e.g. TomcatService).
            when (resolved) {
                is KtProperty -> {
                    ArmeriaKotlinDecoratorChainSupport.resolveKotlinTypeReferenceText(resolved.typeReference)
                        ?.let { return it }
                    resolved.initializer?.let { return renderKotlinTarget(it) }
                }
                is PsiVariable -> return resolved.type.presentableText
            }
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
