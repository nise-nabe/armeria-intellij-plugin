package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
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

    private const val ARMERIA_PACKAGE_PREFIX = "com.linecorp.armeria"
    private const val ARMERIA_SERVER_PACKAGE_PREFIX = "com.linecorp.armeria.server"
    private const val SERVER_BUILDER_CLASS = "com.linecorp.armeria.server.ServerBuilder"
    private const val ARMERIA_HEADER_SCAN_LIMIT = 4096
    private val REGISTRATION_METHOD_NAMES = setOf("service", "serviceUnder", "annotatedService")
    private val ARMERIA_REFERENCE_PATTERN =
        Regex("""(?<![\w"])com\.linecorp\.armeria(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")

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
        val serverBuilderOnClasspath = psiFacade.findClass(SERVER_BUILDER_CLASS, scope) != null

        collectAnnotatedRoutesIndexed(project, scope, routes)
        collectServiceRegistrationsIndexed(project, scope, routes, seenServiceRegistrations)
        if (!serverBuilderOnClasspath) {
            collectServiceRegistrationsFallback(project, scope, routes, fallbackScannedFiles, seenServiceRegistrations)
        }
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteCollector.collect(project, scope, routes, seenServiceRegistrations)
        }

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
        val builderClass = psiFacade.findClass(SERVER_BUILDER_CLASS, scope) ?: return
        for (methodName in REGISTRATION_METHOD_NAMES) {
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
            if (!referencesArmeria(psiFile)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectRoutesFromFile(psiFile, routes, seenServiceRegistrations)
        }
    }

    private fun referencesArmeria(file: PsiJavaFile): Boolean {
        val hasArmeriaImports = file.importList
            ?.allImportStatements
            ?.any { statement ->
                statement.importReference?.qualifiedName?.startsWith(ARMERIA_PACKAGE_PREFIX) == true
            } ?: false
        if (hasArmeriaImports) {
            return true
        }
        val contents = file.viewProvider.contents
        val searchWindow = contents.subSequence(0, minOf(contents.length, ARMERIA_HEADER_SCAN_LIMIT))
        return ARMERIA_REFERENCE_PATTERN.containsMatchIn(searchWindow)
    }

    private fun collectRoutesFromFile(
        file: PsiJavaFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                ArmeriaRouteCollectionMetrics.current()?.methodCallsVisited?.incrementAndGet()
                val methodName = expression.methodExpression.referenceName
                if (methodName !in REGISTRATION_METHOD_NAMES) {
                    super.visitMethodCallExpression(expression)
                    return
                }
                if (!looksLikeArmeriaBuilderCall(expression)) {
                    super.visitMethodCallExpression(expression)
                    return
                }
                addServiceRegistrationFromCall(expression, routes, seenServiceRegistrations)
                super.visitMethodCallExpression(expression)
            }
        })
    }

    internal fun addServiceRegistrationFromCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val registrationKey = serviceRegistrationKey(expression) ?: return
        if (!seenServiceRegistrations.add(registrationKey)) {
            return
        }
        val methodName = expression.methodExpression.referenceName ?: return
        val arguments = expression.argumentList.expressions
        val path = extractRegistrationPath(methodName, arguments) ?: return
        val implementationExpression = when (methodName) {
            "annotatedService" -> arguments.getOrNull(1) ?: arguments.getOrNull(0)
            else -> arguments.getOrNull(1)
        } ?: return
        val protocol = ArmeriaRouteTargetExtractor.detectProtocol(implementationExpression.text)
        val target = ArmeriaRouteTargetExtractor.extractTarget(implementationExpression)
        val targetUnresolved = ArmeriaRouteTargetExtractor.isUnresolvedTarget(implementationExpression, target)
        val registrationMethod = RegistrationMethod.fromMethodName(methodName) ?: return
        val routeMatch = resolveRouteMatch(registrationMethod, protocol)
        val annotatedServiceHasPathPrefix =
            registrationMethod == RegistrationMethod.ANNOTATED_SERVICE && arguments.size > 1
        routes += ArmeriaRoute.create(
            element = expression,
            protocol = protocol.presentableName(),
            httpMethod = "",
            path = ArmeriaRouteSupport.normalizePath(path),
            target = target,
            routeMatch = routeMatch,
            targetUnresolved = targetUnresolved,
            isDocService = protocol == RouteProtocol.DOC_SERVICE,
            annotatedServiceHasPathPrefix = annotatedServiceHasPathPrefix,
        )
    }

    private fun resolveRouteMatch(registrationMethod: RegistrationMethod, protocol: RouteProtocol): RouteMatch {
        if (protocol != RouteProtocol.HTTP) {
            return RouteMatch.NON_HTTP
        }
        return when (registrationMethod) {
            RegistrationMethod.SERVICE -> RouteMatch.SERVICE
            RegistrationMethod.ANNOTATED_SERVICE -> RouteMatch.ANNOTATED_SERVICE
            RegistrationMethod.SERVICE_UNDER -> RouteMatch.SERVICE_UNDER
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
        return qualifierText.contains("Server.builder()") || qualifierText.contains("serverBuilder")
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        return resolvedClass?.startsWith(ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun buildMethodTarget(psiClass: PsiClass, method: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }

    private enum class RegistrationMethod {
        SERVICE,
        SERVICE_UNDER,
        ANNOTATED_SERVICE;

        companion object {
            fun fromMethodName(name: String): RegistrationMethod? {
                return when (name) {
                    "service" -> SERVICE
                    "serviceUnder" -> SERVICE_UNDER
                    "annotatedService" -> ANNOTATED_SERVICE
                    else -> null
                }
            }
        }
    }

    private fun extractRegistrationPath(methodName: String, arguments: Array<PsiExpression>): String? {
        val method = RegistrationMethod.fromMethodName(methodName) ?: return null
        return when (method) {
            RegistrationMethod.SERVICE, RegistrationMethod.SERVICE_UNDER -> extractString(arguments.getOrNull(0))
            RegistrationMethod.ANNOTATED_SERVICE -> if (arguments.size > 1) extractString(arguments.getOrNull(0)) else "/"
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

    private fun isKotlinPluginAvailable(): Boolean {
        return try {
            Class.forName("org.jetbrains.kotlin.idea.KotlinFileType")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

}
