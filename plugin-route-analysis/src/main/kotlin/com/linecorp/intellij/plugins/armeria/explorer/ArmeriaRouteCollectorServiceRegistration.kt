package com.linecorp.intellij.plugins.armeria.explorer

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

internal object ArmeriaRouteCollectorServiceRegistration {
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
            if (!ArmeriaRouteCollector.referencesArmeriaJavaContent(psiFile)) {
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
        val arguments = expression.argumentList.expressions
        val path = extractRegistrationPath(methodName, arguments) ?: return false
        val implementationExpression =
            when (CoreServiceRegistrationMethod.fromMethodName(methodName)) {
                CoreServiceRegistrationMethod.ANNOTATED_SERVICE -> arguments.getOrNull(1) ?: arguments.getOrNull(0)
                CoreServiceRegistrationMethod.SERVICE, CoreServiceRegistrationMethod.SERVICE_UNDER -> arguments.getOrNull(1)
                null -> null
            } ?: return false
        val target = ArmeriaRouteTargetExtractor.extractTarget(implementationExpression)
        return addServiceRegistrationRoute(
            element = expression,
            registrationKey = registrationKey,
            methodName = methodName,
            path = path,
            target = target,
            targetUnresolved = ArmeriaRouteTargetExtractor.isUnresolvedTarget(implementationExpression, target),
            implementationText = implementationExpression.text,
            argumentCount = arguments.size,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
        )
    }

    fun addServiceRegistrationRoute(
        element: PsiElement,
        registrationKey: String,
        methodName: String,
        path: String,
        target: String,
        targetUnresolved: Boolean,
        implementationText: String,
        argumentCount: Int,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
        decorators: List<String>? = null,
    ): Boolean {
        if (!seenServiceRegistrations.add(registrationKey)) {
            return false
        }
        val registrationMethod = CoreServiceRegistrationMethod.fromMethodName(methodName) ?: return false
        val protocol = ArmeriaRouteTargetExtractor.detectProtocol(implementationText)
        val routeMatch = resolveRouteMatch(registrationMethod, protocol)
        val annotatedServiceHasPathPrefix =
            registrationMethod == CoreServiceRegistrationMethod.ANNOTATED_SERVICE && argumentCount > 1
        val normalizedPath = ArmeriaRouteSupport.normalizePath(path)
        val programmaticDecorators = decorators ?: ArmeriaRouteCollector.collectProgrammaticDecorators(element, normalizedPath)
        val timeoutHints = ArmeriaRouteCollector.collectBuilderTimeoutHints(element)
        routes +=
            ArmeriaRoute.create(
                element = element,
                protocol = protocol.presentableName(),
                httpMethod = "",
                path = normalizedPath,
                target = target,
                routeMatch = routeMatch,
                targetUnresolved = targetUnresolved,
                isDocService = protocol == RouteProtocol.DOC_SERVICE,
                annotatedServiceHasPathPrefix = annotatedServiceHasPathPrefix,
                decorators = programmaticDecorators,
                timeoutHints = timeoutHints,
            )
        return true
    }

    private fun resolveRouteMatch(
        registrationMethod: CoreServiceRegistrationMethod,
        protocol: RouteProtocol,
    ): RouteMatch {
        if (protocol != RouteProtocol.HTTP) {
            return RouteMatch.NON_HTTP
        }
        return when (registrationMethod) {
            CoreServiceRegistrationMethod.SERVICE -> RouteMatch.SERVICE
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE -> RouteMatch.ANNOTATED_SERVICE
            CoreServiceRegistrationMethod.SERVICE_UNDER -> RouteMatch.SERVICE_UNDER
        }
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
