package com.linecorp.intellij.plugins.armeria.explorer.collector
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.collector.decorator.ArmeriaKotlinDecoratorChainSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin.ArmeriaKotlinExtendedRegistrationCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.CoreServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteTargetExtractor
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument

object ArmeriaKotlinRouteCollector {
    private val BUILDER_CHAIN_METHODS = setOf("build", "addService", "addServices", "enableUnframedRequests")

    fun referencesArmeriaKotlinContent(file: KtFile): Boolean {
        val hasArmeriaImports =
            file.importList?.imports?.any { import ->
                import.importedFqName?.asString()?.let(ArmeriaRouteSupport::isArmeriaQualifiedName) == true
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

    fun collectServiceRegistrationsInScope(
        root: PsiElement,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        root.forEachDescendant { element ->
            val call = element as? KtCallExpression ?: return@forEachDescendant
            ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return@forEachDescendant
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

    fun looksLikeArmeriaBuilderCall(call: KtCallExpression): Boolean = ArmeriaBuilderCallHeuristics.looksLikeKotlinBuilderCall(call)

    fun addServiceRegistrationFromCall(
        call: KtCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ): Boolean {
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return false
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
        val registrationKey =
            ArmeriaRouteSupport.registrationKey(
                virtualFile.path,
                call.textRange,
                methodName,
            )
        val arguments = call.valueArguments
        val registrationMethod = CoreServiceRegistrationMethod.fromMethodName(methodName) ?: return
        val pathlessSaml = isPathlessSamlServiceCall(registrationMethod, arguments)
        val implementationExpression =
            if (pathlessSaml) {
                ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "service", 0)
            } else {
                resolveServiceExpression(methodName, arguments)
            } ?: return
        val unwrappedImplementation = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(implementationExpression) ?: return
        val targetExpression = extractKotlinTargetExpression(unwrappedImplementation)
        val target = renderKotlinTarget(targetExpression)
        val targetUnresolved = isUnresolvedKotlinTarget(targetExpression, target)
        val serviceTypeHint = extractKotlinKnownServiceType(unwrappedImplementation).orEmpty()
        if (pathlessSaml) {
            val kind = ArmeriaKnownHttpServiceClassifier.classify(serviceTypeHint)
            if (!ArmeriaKnownHttpServiceClassifier.isSaml(kind)) {
                return
            }
            ArmeriaRouteCollectorServiceRegistration.addSamlDefaultPathRoutes(
                element = call,
                registrationKey = registrationKey,
                methodName = methodName,
                target = target,
                targetUnresolved = targetUnresolved,
                serviceTypeHint = serviceTypeHint,
                argumentCount = arguments.size,
                routes = routes,
                seenServiceRegistrations = seenServiceRegistrations,
                serviceExpression = unwrappedImplementation,
            )
            return
        }
        val path = extractRegistrationPath(methodName, arguments) ?: return
        ArmeriaRouteCollectorServiceRegistration.addServiceRegistrationRoute(
            element = call,
            registrationKey = registrationKey,
            methodName = methodName,
            path = path,
            target = target,
            targetUnresolved = targetUnresolved,
            serviceTypeHint = serviceTypeHint,
            argumentCount = arguments.size,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
            serviceExpression = unwrappedImplementation,
        )
    }

    private fun isPathlessSamlServiceCall(
        registrationMethod: CoreServiceRegistrationMethod,
        arguments: List<KtValueArgument>,
    ): Boolean {
        if (registrationMethod != CoreServiceRegistrationMethod.SERVICE || arguments.isEmpty()) {
            return false
        }
        val pathExpression = ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "path", 0)
        return ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(pathExpression) == null
    }

    private fun resolveServiceExpression(
        methodName: String,
        arguments: List<KtValueArgument>,
    ): KtExpression? =
        when (CoreServiceRegistrationMethod.fromMethodName(methodName)) {
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE ->
                ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "service", 1)
                    ?: ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "service", 0)
            CoreServiceRegistrationMethod.SERVICE, CoreServiceRegistrationMethod.SERVICE_UNDER ->
                ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "service", 1)
            null -> null
        }

    private fun findPathPrefixArgument(
        arguments: List<KtValueArgument>,
        positionalIndex: Int,
    ): KtExpression? =
        ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "pathPrefix", positionalIndex)
            ?: ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "prefix", positionalIndex)

    private fun extractRegistrationPath(
        methodName: String,
        arguments: List<KtValueArgument>,
    ): String? =
        when (CoreServiceRegistrationMethod.fromMethodName(methodName)) {
            CoreServiceRegistrationMethod.SERVICE ->
                ArmeriaKotlinExpressionSupport.extractKotlinString(
                    ArmeriaKotlinExpressionSupport.findArgumentExpression(arguments, "path", 0),
                )
            CoreServiceRegistrationMethod.SERVICE_UNDER ->
                ArmeriaKotlinExpressionSupport.extractKotlinString(findPathPrefixArgument(arguments, 0))
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE -> {
                if (arguments.size > 1) {
                    ArmeriaKotlinExpressionSupport.extractKotlinString(findPathPrefixArgument(arguments, 0))
                } else {
                    "/"
                }
            }
            null -> null
        }

    fun extractKotlinStrings(expression: KtExpression?): List<String> {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return emptyList()
        if (unwrapped is KtCollectionLiteralExpression) {
            return unwrapped.getInnerExpressions().mapNotNull(ArmeriaKotlinExpressionSupport::extractKotlinString)
        }
        return ArmeriaKotlinExpressionSupport.extractKotlinString(unwrapped)?.let { listOf(it) }.orEmpty()
    }

    private fun extractKotlinKnownServiceType(
        expression: KtExpression,
        visitedProperties: MutableSet<KtProperty> = mutableSetOf(),
    ): String? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        if (unwrapped is KtDotQualifiedExpression) {
            unwrapped.selectorExpression?.let { selector ->
                ArmeriaKnownHttpServiceClassifier
                    .knownServiceTypeNameOrNull(extractKotlinKnownServiceType(selector, visitedProperties))
                    ?.let { return it }
            }
            return extractKotlinKnownServiceType(unwrapped.receiverExpression, visitedProperties)
        }
        if (unwrapped is KtCallExpression) {
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(unwrapped)
            val receiver = dotQualifiedReceiver(unwrapped.calleeExpression, unwrapped)
            if (methodName in BUILDER_CHAIN_METHODS || methodName == "builder" || methodName == "of") {
                if (receiver != null) {
                    ArmeriaKnownHttpServiceClassifier
                        .knownServiceTypeNameOrNull(extractKotlinKnownServiceType(receiver, visitedProperties))
                        ?.let { return it }
                }
            }
            val callee = unwrapped.calleeExpression
            val reference =
                callee as? KtNameReferenceExpression ?: callee?.let {
                    if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
                }
            val resolved = reference?.references?.firstOrNull()?.resolve()
            val fromResolved =
                resolveQualifiedClassName(resolved)?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
            ArmeriaKnownHttpServiceClassifier.knownServiceTypeNameOrNull(fromResolved)?.let { return it }
            if (receiver != null) {
                extractKotlinKnownServiceType(receiver, visitedProperties)?.let { return it }
            }
            return fromResolved
        }
        if (unwrapped is KtNameReferenceExpression) {
            return extractKotlinKnownServiceTypeFromName(unwrapped, visitedProperties)
        }
        return null
    }

    private fun extractKotlinKnownServiceTypeFromName(
        expression: KtNameReferenceExpression,
        visitedProperties: MutableSet<KtProperty>,
    ): String? {
        when (val resolved = expression.references.firstOrNull()?.resolve()) {
            is KtParameter -> {
                return ArmeriaKotlinDecoratorChainSupport
                    .resolveKotlinTypeReferenceText(resolved.typeReference)
                    ?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
            }
            is KtProperty -> {
                val declaredType =
                    ArmeriaKotlinDecoratorChainSupport
                        .resolveKotlinTypeReferenceText(resolved.typeReference)
                        ?.let(ArmeriaKnownHttpServiceClassifier::canonicalServiceTypeName)
                ArmeriaKnownHttpServiceClassifier.knownServiceTypeNameOrNull(declaredType)?.let { return it }
                val initializer = resolved.initializer
                if (initializer != null && visitedProperties.add(resolved)) {
                    extractKotlinKnownServiceType(initializer, visitedProperties)
                        ?.let(ArmeriaKnownHttpServiceClassifier::knownServiceTypeNameOrNull)
                        ?.let { return it }
                }
                return declaredType
            }
            is PsiVariable -> {
                val declaredType =
                    ArmeriaKnownHttpServiceClassifier.canonicalServiceTypeName(resolved.type.canonicalText)
                ArmeriaKnownHttpServiceClassifier.knownServiceTypeNameOrNull(declaredType)?.let { return it }
                val initializer = resolved.initializer ?: return declaredType
                return ArmeriaRouteTargetExtractor
                    .extractKnownServiceType(initializer)
                    ?.let(ArmeriaKnownHttpServiceClassifier::knownServiceTypeNameOrNull)
                    ?: declaredType
            }
            else -> return resolveQualifiedClassName(resolved)
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
            val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(expression)
            if (methodName in BUILDER_CHAIN_METHODS) {
                val receiver = dotQualifiedReceiver(expression.calleeExpression, expression) ?: return expression
                return extractKotlinTargetExpression(receiver)
            }
            if (methodName == "builder") {
                expression.valueArguments.firstOrNull()?.getArgumentExpression()?.let { serviceArg ->
                    return extractKotlinTargetExpression(serviceArg)
                }
                val receiver = dotQualifiedReceiver(expression.calleeExpression, expression)
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

    private fun renderKotlinTarget(
        expression: KtExpression,
        visitedProperties: MutableSet<KtProperty> = mutableSetOf(),
    ): String {
        if (expression is KtCallExpression) {
            val callee = expression.calleeExpression
            val reference =
                callee as? KtNameReferenceExpression ?: callee?.let {
                    if (it is KtDotQualifiedExpression) it.selectorExpression as? KtNameReferenceExpression else null
                }
            val resolved = reference?.references?.firstOrNull()?.resolve()
            resolveQualifiedClassName(resolved)?.let { return it }
            return reference?.getReferencedName() ?: expression.text
        }
        if (expression is KtNameReferenceExpression) {
            ArmeriaKotlinDecoratorChainSupport
                .resolveKotlinTypedNameTarget(
                    expression,
                    visitedProperties,
                    ::renderKotlinTarget,
                )?.let { return it }
            return expression.text
        }
        return expression.text
    }

    private fun isUnresolvedKotlinTarget(
        expression: KtExpression,
        extractedTarget: String,
    ): Boolean {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return true
        val rawTarget = expression.text.trim()
        return when (unwrapped) {
            is KtCallExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                val callee = unwrapped.calleeExpression
                val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(unwrapped)
                if (methodName != null && extractedTarget == methodName) {
                    true
                } else {
                    val reference =
                        callee as? KtNameReferenceExpression ?: callee?.let {
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

    private fun dotQualifiedReceiver(
        callee: org.jetbrains.kotlin.psi.KtExpression?,
        expression: KtCallExpression,
    ): KtExpression? =
        when (callee) {
            is KtDotQualifiedExpression -> callee.receiverExpression
            else -> (expression.parent as? KtDotQualifiedExpression)?.receiverExpression
        }

    private fun resolveQualifiedClassName(resolved: PsiElement?): String? =
        when (resolved) {
            is com.intellij.psi.PsiClass -> resolved.qualifiedName
            is PsiMethod -> resolved.containingClass?.qualifiedName
            is KtClassOrObject -> resolved.fqName?.asString()
            is KtConstructor<*> -> resolved.getContainingClassOrObject().fqName?.asString()
            else -> null
        }

    private fun isResolvedKotlinClass(resolved: PsiElement?): Boolean = resolved is com.intellij.psi.PsiClass || resolved is KtClassOrObject

    private fun isResolvedKotlinConstructor(resolved: PsiElement?): Boolean =
        resolved is PsiMethod && resolved.isConstructor || resolved is KtConstructor<*>
}
