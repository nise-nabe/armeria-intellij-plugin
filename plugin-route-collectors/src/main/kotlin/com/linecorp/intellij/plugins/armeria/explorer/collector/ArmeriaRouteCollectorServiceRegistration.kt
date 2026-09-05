package com.linecorp.intellij.plugins.armeria.explorer.collector
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java.ArmeriaExtendedRegistrationCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.CoreServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaBuilderMetadataSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaDelegationSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaGrpcServiceOptionsSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteTargetExtractor
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteCollectorServiceRegistration {
    fun collectServiceRegistrationsIndexed(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val builderClass = psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) ?: return
        for (methodName in CoreServiceRegistrationMethod.METHOD_NAMES) {
            for (method in builderClass.findMethodsByName(methodName, false)) {
                ReferencesSearch.search(method, scope).forEach { reference ->
                    val call =
                        PsiTreeUtil.getParentOfType(reference.element, PsiMethodCallExpression::class.java)
                            ?: return@forEach
                    if (call.methodExpression.referenceName != methodName) {
                        return@forEach
                    }
                    addServiceRegistrationFromCall(call, routes, seenServiceRegistrations)
                }
            }
        }
    }

    fun collectServiceRegistrationsFallback(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<com.intellij.openapi.vfs.VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!ArmeriaRouteSupport.referencesArmeriaJavaContent(psiFile)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectServiceRegistrationsFromJavaFile(psiFile, routes, seenServiceRegistrations)
        }
    }

    fun collectServiceRegistrationsFromJavaFile(
        file: PsiJavaFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        file.accept(
            object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    collectServiceRegistrationFromMethodCall(expression, routes, seenServiceRegistrations)
                    ArmeriaExtendedRegistrationCollector.visitMethodCallExpression(
                        expression,
                        routes,
                        seenServiceRegistrations,
                    )
                    super.visitMethodCallExpression(expression)
                }
            },
        )
    }

    fun collectServiceRegistrationFromMethodCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
        val methodName = expression.methodExpression.referenceName
        if (methodName !in CoreServiceRegistrationMethod.METHOD_NAMES) {
            return
        }
        if (!ArmeriaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)) {
            return
        }
        addServiceRegistrationFromCall(expression, routes, seenServiceRegistrations)
    }

    fun addServiceRegistrationFromCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ): Boolean {
        val registrationKey = serviceRegistrationKey(expression) ?: return false
        val methodName = expression.methodExpression.referenceName ?: return false
        val registrationMethod = CoreServiceRegistrationMethod.fromMethodName(methodName) ?: return false
        val arguments = expression.argumentList.expressions
        val pathlessSaml = isPathlessSamlServiceCall(registrationMethod, arguments)
        val implementationExpression =
            if (pathlessSaml) {
                arguments.getOrNull(0)
            } else {
                when (registrationMethod) {
                    CoreServiceRegistrationMethod.ANNOTATED_SERVICE ->
                        arguments.getOrNull(1) ?: arguments.getOrNull(0)
                    CoreServiceRegistrationMethod.SERVICE, CoreServiceRegistrationMethod.SERVICE_UNDER ->
                        arguments.getOrNull(1)
                }
            } ?: return false
        val target = ArmeriaRouteTargetExtractor.extractTarget(implementationExpression)
        val serviceTypeHint =
            ArmeriaRouteTargetExtractor.extractKnownServiceType(implementationExpression).orEmpty()
        val targetUnresolved = ArmeriaRouteTargetExtractor.isUnresolvedTarget(implementationExpression, target)
        if (pathlessSaml) {
            val kind = ArmeriaKnownHttpServiceClassifier.classify(serviceTypeHint)
            if (!ArmeriaKnownHttpServiceClassifier.isSaml(kind)) {
                return false
            }
            return addSamlDefaultPathRoutes(
                element = expression,
                registrationKey = registrationKey,
                methodName = methodName,
                target = target,
                targetUnresolved = targetUnresolved,
                serviceTypeHint = serviceTypeHint,
                argumentCount = arguments.size,
                routes = routes,
                seenServiceRegistrations = seenServiceRegistrations,
                serviceExpression = implementationExpression,
            )
        }
        val path = extractRegistrationPath(methodName, arguments) ?: return false
        return addServiceRegistrationRoute(
            element = expression,
            registrationKey = registrationKey,
            methodName = methodName,
            path = path,
            target = target,
            targetUnresolved = targetUnresolved,
            serviceTypeHint = serviceTypeHint,
            argumentCount = arguments.size,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
            serviceExpression = implementationExpression,
        )
    }

    fun addSamlDefaultPathRoutes(
        element: PsiElement,
        registrationKey: String,
        methodName: String,
        target: String,
        targetUnresolved: Boolean,
        serviceTypeHint: String,
        argumentCount: Int,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
        serviceExpression: PsiElement? = null,
    ): Boolean {
        var added = false
        for (path in ArmeriaKnownHttpServiceClassifier.SAML_DEFAULT_PATHS) {
            val emitted =
                addServiceRegistrationRoute(
                    element = element,
                    registrationKey = "$registrationKey:$path",
                    methodName = methodName,
                    path = path,
                    target = target,
                    targetUnresolved = targetUnresolved,
                    serviceTypeHint = serviceTypeHint,
                    argumentCount = argumentCount,
                    routes = routes,
                    seenServiceRegistrations = seenServiceRegistrations,
                    serviceExpression = serviceExpression,
                )
            if (emitted) {
                added = true
            }
        }
        return added
    }

    fun addServiceRegistrationRoute(
        element: PsiElement,
        registrationKey: String,
        methodName: String,
        path: String,
        target: String,
        targetUnresolved: Boolean,
        serviceTypeHint: String,
        argumentCount: Int,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
        decorators: List<String>? = null,
        sourceOffset: Int? = null,
        serviceExpression: PsiElement? = null,
    ): Boolean {
        if (!seenServiceRegistrations.add(registrationKey)) {
            return false
        }
        val registrationMethod = CoreServiceRegistrationMethod.fromMethodName(methodName) ?: return false
        val kind = ArmeriaKnownHttpServiceClassifier.classify(serviceTypeHint)
        val protocol = ArmeriaKnownHttpServiceClassifier.protocol(kind)
        val routeMatch = ArmeriaKnownHttpServiceClassifier.routeMatch(kind, registrationMethod)
        val httpMethod = ArmeriaKnownHttpServiceClassifier.defaultHttpMethod(kind)
        val annotatedServiceHasPathPrefix =
            registrationMethod == CoreServiceRegistrationMethod.ANNOTATED_SERVICE && argumentCount > 1
        val normalizedPath = ArmeriaRouteSupport.normalizePath(path)
        val programmaticDecorators = decorators ?: ArmeriaBuilderMetadataSupport.collectProgrammaticDecorators(element, normalizedPath)
        val timeoutHints = ArmeriaBuilderMetadataSupport.collectBuilderTimeoutHints(element)
        val delegationKind: DelegationKind? =
            if (protocol == RouteProtocol.HTTP) {
                ArmeriaDelegationSupport.detectDelegation(target, routeMatch)
            } else {
                null
            }
        routes +=
            ArmeriaRoute.create(
                element = element,
                protocol = protocol.presentableName(),
                httpMethod = httpMethod,
                path = normalizedPath,
                target = target,
                routeMatch = routeMatch,
                targetUnresolved = targetUnresolved,
                isDocService = ArmeriaKnownHttpServiceClassifier.isDocService(kind),
                excludeFromDuplicateIndex = ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex(kind),
                annotatedServiceHasPathPrefix = annotatedServiceHasPathPrefix,
                decorators = programmaticDecorators,
                timeoutHints = timeoutHints,
                contentHints =
                    sseContentHints(kind) +
                        ArmeriaGrpcServiceOptionsSupport.contentHints(serviceExpression, kind),
                delegationKind = delegationKind,
                sourceOffset = sourceOffset,
            )
        return true
    }

    private fun sseContentHints(kind: KnownHttpServiceKind): List<String> =
        if (kind == KnownHttpServiceKind.SSE) {
            listOf(message("route.explorer.hint.produces", "text/event-stream"))
        } else {
            emptyList()
        }

    private fun serviceRegistrationKey(expression: PsiMethodCallExpression): String? {
        val virtualFile = expression.containingFile?.virtualFile ?: return null
        val methodName = expression.methodExpression.referenceName ?: return null
        return ArmeriaRouteSupport.registrationKey(
            virtualFile.path,
            expression.textRange,
            methodName,
        )
    }

    private fun extractRegistrationPath(
        methodName: String,
        arguments: Array<PsiExpression>,
    ): String? =
        when (CoreServiceRegistrationMethod.fromMethodName(methodName)) {
            CoreServiceRegistrationMethod.SERVICE, CoreServiceRegistrationMethod.SERVICE_UNDER ->
                extractString(arguments.getOrNull(0))
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE ->
                if (arguments.size > 1) extractString(arguments.getOrNull(0)) else "/"
            null -> null
        }

    private fun isPathlessSamlServiceCall(
        registrationMethod: CoreServiceRegistrationMethod,
        arguments: Array<PsiExpression>,
    ): Boolean {
        if (registrationMethod != CoreServiceRegistrationMethod.SERVICE || arguments.isEmpty()) {
            return false
        }
        return extractConstantString(arguments[0]) == null
    }

    private fun extractConstantString(expression: PsiExpression): String? =
        when (expression) {
            is PsiLiteralExpression -> expression.value as? String
            else ->
                JavaPsiFacade
                    .getInstance(expression.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(expression) as? String
        }

    private fun extractString(expression: PsiExpression?): String? =
        when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> {
                val constantValue =
                    JavaPsiFacade
                        .getInstance(expression.project)
                        .constantEvaluationHelper
                        .computeConstantExpression(expression) as? String
                constantValue ?: expression.text.takeIf { StringUtil.isNotEmpty(it) }
            }
        }
}
