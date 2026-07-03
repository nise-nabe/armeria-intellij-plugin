package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal enum class RouteProtocol(private val messageKey: String) {
    HTTP("route.explorer.protocol.http"),
    GRPC("route.explorer.protocol.grpc"),
    DOC_SERVICE("route.explorer.protocol.docService"),
    THRIFT("route.explorer.protocol.thrift"),
    ;

    fun presentableName(): String = message(messageKey)
}

object ArmeriaRouteCollector {

    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun collect(project: Project): List<ArmeriaRoute> {
        val metrics = ArmeriaRouteCollectionMetrics()
        val startedAt = System.nanoTime()
        val routes = ArmeriaRouteCollectionMetrics.runWith(metrics) {
            CachedValuesManager.getManager(project).getCachedValue(project) {
                computeProjectRoutes(project)
            }
        }
        metrics.elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        ArmeriaRouteCollectionMetrics.logIfEnabled(metrics.snapshot())
        return routes
    }

    private fun computeProjectRoutes(project: Project): CachedValueProvider.Result<List<ArmeriaRoute>> {
        val scope = collectionScope(project)
        val routes = mutableListOf<ArmeriaRoute>()
        val fallbackScannedFiles = linkedSetOf<VirtualFile>()
        val seenServiceRegistrations = mutableSetOf<String>()
        val psiFacade = JavaPsiFacade.getInstance(project)
        val serverBuilderOnClasspath = psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) != null

        collectAnnotatedRoutesIndexed(project, scope, routes)
        collectServiceRegistrationsIndexed(project, scope, routes, seenServiceRegistrations)
        if (!serverBuilderOnClasspath) {
            collectServiceRegistrationsFallback(project, scope, routes, fallbackScannedFiles, seenServiceRegistrations)
        }
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteCollector.collectServiceRegistrationsFallback(
                project,
                scope,
                routes,
                fallbackScannedFiles,
                seenServiceRegistrations,
            )
        }
        ArmeriaSpringBootRouteCollector.collect(
            project,
            scope,
            routes,
            fallbackScannedFiles,
            seenServiceRegistrations,
        )

        return CachedValueProvider.Result.create(
            routes.sortedWith(
                compareBy(ArmeriaRoute::moduleName, ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target),
            ),
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }

    private fun collectionScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.projectScope(project)

    private fun collectAnnotatedRoutesIndexed(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val seenMethods = mutableSetOf<PsiMethod>()
        for (annotationFqn in ArmeriaRouteSupport.routeAnnotations.keys) {
            val annotationClass = psiFacade.findClass(annotationFqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope).forEach { method ->
                if (!seenMethods.add(method)) {
                    return@forEach
                }
                addAnnotatedRouteFromMethod(method, routes)
            }
        }
    }

    internal fun addAnnotatedRouteFromMethod(method: PsiMethod, routes: MutableList<ArmeriaRoute>) {
        val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return
        val containingClass = method.containingClass ?: return
        val classPrefix =
            ArmeriaRouteSupport.extractPrimaryPath(containingClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION))
        val classDecorators =
            ArmeriaRouteSupport.extractNames(containingClass.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
        val classExceptionHandlers =
            ArmeriaRouteSupport.extractNames(containingClass.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION))
        val paths = ArmeriaRouteSupport.extractPaths(annotation.first).ifEmpty { listOf("/") }
        val methodDecorators =
            classDecorators + ArmeriaRouteSupport.extractNames(method.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
        val methodExceptionHandlers = classExceptionHandlers + ArmeriaRouteSupport.extractNames(
            method.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION),
        )
        val target = buildMethodTarget(containingClass, method)
        val executionHints = ArmeriaTimeoutSupport.collectExecutionHints(method)
        for (path in paths) {
            routes += ArmeriaRoute.create(
                element = method,
                protocol = RouteProtocol.HTTP.presentableName(),
                httpMethod = annotation.second,
                path = ArmeriaRouteSupport.combinePaths(classPrefix, path),
                target = target,
                routeMatch = RouteMatch.ANNOTATED_HTTP,
                decorators = methodDecorators.distinct(),
                exceptionHandlers = methodExceptionHandlers.distinct(),
                executionHints = executionHints,
            )
        }
    }

    private fun collectServiceRegistrationsIndexed(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val builderClass = psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) ?: return
        for (methodName in ServiceRegistrationMethod.METHOD_NAMES) {
            for (method in builderClass.findMethodsByName(methodName, false)) {
                ReferencesSearch.search(method, scope).forEach { reference ->
                    val call = PsiTreeUtil.getParentOfType(reference.element, PsiMethodCallExpression::class.java)
                        ?: return@forEach
                    if (call.methodExpression.referenceName != methodName) {
                        return@forEach
                    }
                    addServiceRegistrationFromCall(call, routes, seenServiceRegistrations)
                }
            }
        }
    }

    private fun collectServiceRegistrationsFallback(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!referencesArmeriaJavaContent(psiFile)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectServiceRegistrationsFromJavaFile(psiFile, routes, seenServiceRegistrations)
        }
    }

    internal fun referencesArmeriaJavaContent(file: PsiJavaFile): Boolean {
        val hasArmeriaImports = file.importList
            ?.allImportStatements
            ?.any { statement ->
                statement.importReference?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX) == true
            } ?: false
        if (hasArmeriaImports) {
            return true
        }
        return ArmeriaRouteSupport.referencesArmeriaInText(file.viewProvider.contents)
    }

    internal fun collectServiceRegistrationsFromJavaFile(
        file: PsiJavaFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                collectServiceRegistrationFromMethodCall(expression, routes, seenServiceRegistrations)
                super.visitMethodCallExpression(expression)
            }
        })
    }

    internal fun collectServiceRegistrationFromMethodCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
        val methodName = expression.methodExpression.referenceName
        if (methodName !in ServiceRegistrationMethod.METHOD_NAMES) {
            return
        }
        if (!looksLikeArmeriaBuilderCall(expression)) {
            return
        }
        addServiceRegistrationFromCall(expression, routes, seenServiceRegistrations)
    }

    internal fun addServiceRegistrationFromCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val registrationKey = serviceRegistrationKey(expression) ?: return
        val methodName = expression.methodExpression.referenceName ?: return
        val arguments = expression.argumentList.expressions
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> arguments.getOrNull(1) ?: arguments.getOrNull(0)
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER -> arguments.getOrNull(1)
            null -> null
        } ?: return
        val target = ArmeriaRouteTargetExtractor.extractTarget(implementationExpression)
        addServiceRegistrationRoute(
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

    internal fun addServiceRegistrationRoute(
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
    ) {
        if (!seenServiceRegistrations.add(registrationKey)) {
            return
        }
        val registrationMethod = ServiceRegistrationMethod.fromMethodName(methodName) ?: return
        val protocol = ArmeriaRouteTargetExtractor.detectProtocol(implementationText)
        val routeMatch = resolveRouteMatch(registrationMethod, protocol)
        val annotatedServiceHasPathPrefix =
            registrationMethod == ServiceRegistrationMethod.ANNOTATED_SERVICE && argumentCount > 1
        val normalizedPath = ArmeriaRouteSupport.normalizePath(path)
        val programmaticDecorators = decorators ?: collectProgrammaticDecorators(element, normalizedPath)
        val timeoutHints = (element as? PsiMethodCallExpression)
            ?.let(ArmeriaTimeoutSupport::collectBuilderTimeoutHints)
            .orEmpty()
        routes += ArmeriaRoute.create(
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
    }

    private fun resolveRouteMatch(registrationMethod: ServiceRegistrationMethod, protocol: RouteProtocol): RouteMatch {
        if (protocol != RouteProtocol.HTTP) {
            return RouteMatch.NON_HTTP
        }
        return when (registrationMethod) {
            ServiceRegistrationMethod.SERVICE -> RouteMatch.SERVICE
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> RouteMatch.ANNOTATED_SERVICE
            ServiceRegistrationMethod.SERVICE_UNDER -> RouteMatch.SERVICE_UNDER
        }
    }

    private fun serviceRegistrationKey(expression: PsiMethodCallExpression): String? {
        val virtualFile = expression.containingFile?.virtualFile ?: return null
        return "${virtualFile.path}:${expression.textRange.startOffset}"
    }

    private fun looksLikeArmeriaBuilderCall(expression: PsiMethodCallExpression): Boolean {
        if (resolvesToArmeriaServerBuilder(expression)) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText)
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        return resolvedClass?.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun buildMethodTarget(psiClass: PsiClass, method: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }

    private fun extractRegistrationPath(methodName: String, arguments: Array<PsiExpression>): String? {
        return when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER ->
                extractString(arguments.getOrNull(0))
            ServiceRegistrationMethod.ANNOTATED_SERVICE ->
                if (arguments.size > 1) extractString(arguments.getOrNull(0)) else "/"
            null -> null
        }
    }

    private fun extractString(expression: PsiExpression?): String? {
        return when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> {
                val constantValue = JavaPsiFacade.getInstance(expression.project)
                    .constantEvaluationHelper
                    .computeConstantExpression(expression) as? String
                constantValue ?: expression.text.takeIf { StringUtil.isNotEmpty(it) }
            }
        }
    }

    private fun isKotlinPluginAvailable(): Boolean =
        PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)

    private fun collectProgrammaticDecorators(element: PsiElement, registrationPath: String): List<String> {
        if (element is PsiMethodCallExpression) {
            return ArmeriaDecoratorSupport.collectProgrammaticDecorators(element, registrationPath)
        }
        if (isKotlinPluginAvailable()) {
            return ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(element, registrationPath)
        }
        return emptyList()
    }

}
