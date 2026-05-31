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
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaRouteCollector {
    private enum class RouteProtocol(private val messageKey: String) {
        HTTP("route.explorer.protocol.http"),
        GRPC("route.explorer.protocol.grpc"),
        DOC_SERVICE("route.explorer.protocol.docService"),
        THRIFT("route.explorer.protocol.thrift"),
        ;

        fun presentableName(): String = message(messageKey)
    }

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

        collectAnnotatedRoutesIndexed(project, scope, routes)
        collectServiceRegistrationsIndexed(project, scope, routes, seenServiceRegistrations)
        collectServiceRegistrationsFallback(project, scope, routes, fallbackScannedFiles, seenServiceRegistrations)

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
                addAnnotatedRoute(method, routes)
            }
        }
    }

    private fun addAnnotatedRoute(method: PsiMethod, routes: MutableList<ArmeriaRoute>) {
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
                kind = message("route.explorer.kind.annotatedService"),
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
                    val call = reference.element.parent as? PsiMethodCallExpression ?: return@forEach
                    if (call.methodExpression.referenceName != methodName) {
                        return@forEach
                    }
                    addServiceRegistration(call, routes, seenServiceRegistrations)
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
                addServiceRegistration(expression, routes, seenServiceRegistrations)
                super.visitMethodCallExpression(expression)
            }
        })
    }

    private fun addServiceRegistration(
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
        val protocol = detectProtocol(implementationExpression.text)
        val target = extractTarget(implementationExpression)
        val targetUnresolved = isUnresolvedTarget(implementationExpression, target)
        val registrationMethod = RegistrationMethod.fromMethodName(methodName) ?: return
        val routeMatch = resolveRouteMatch(registrationMethod, protocol)
        val kind = when (protocol) {
            RouteProtocol.DOC_SERVICE -> message("route.explorer.kind.docService")
            RouteProtocol.GRPC -> message("route.explorer.kind.grpcService")
            RouteProtocol.THRIFT -> message("route.explorer.kind.thriftService")
            else -> if (methodName == "annotatedService") {
                message("route.explorer.kind.annotatedServiceRegistration")
            } else {
                message("route.explorer.kind.serviceRegistration")
            }
        }
        routes += ArmeriaRoute.create(
            element = expression,
            kind = kind,
            protocol = protocol.presentableName(),
            httpMethod = "",
            path = ArmeriaRouteSupport.normalizePath(path),
            target = target,
            routeMatch = routeMatch,
            targetUnresolved = targetUnresolved,
            isDocService = protocol == RouteProtocol.DOC_SERVICE,
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
        val qualifier = expression.methodExpression.qualifierExpression ?: return false
        val qualifierText = qualifier.text
        if (qualifierText.contains("Server.builder()") || qualifierText.contains("serverBuilder")) {
            return resolvesToArmeriaServerBuilder(expression)
        }
        if (mightBeArmeriaBuilderQualifier(qualifierText)) {
            return resolvesToArmeriaServerBuilder(expression)
        }
        if (qualifier is PsiReferenceExpression) {
            return resolvesToArmeriaServerBuilder(expression)
        }
        return false
    }

    private fun resolvesToArmeriaServerBuilder(expression: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        return resolvedClass?.startsWith(ARMERIA_SERVER_PACKAGE_PREFIX) == true
    }

    private fun mightBeArmeriaBuilderQualifier(qualifierText: String): Boolean {
        return qualifierText.contains("Server") ||
            qualifierText.contains("serverBuilder") ||
            qualifierText.contains("armeria", ignoreCase = true)
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

    private fun detectProtocol(expressionText: String): RouteProtocol {
        return when {
            expressionText.contains("GrpcService") -> RouteProtocol.GRPC
            expressionText.contains("DocService") -> RouteProtocol.DOC_SERVICE
            expressionText.contains("Thrift", ignoreCase = true) -> RouteProtocol.THRIFT
            else -> RouteProtocol.HTTP
        }
    }

    private fun isUnresolvedTarget(expression: PsiExpression, extractedTarget: String): Boolean {
        val rawTarget = expression.text.trim()
        val unwrapped = unwrapCast(expression) ?: return true
        return when (unwrapped) {
            is PsiNewExpression -> {
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                unwrapped.classReference?.resolve() == null
            }
            is PsiReferenceExpression -> {
                if (extractedTarget != rawTarget) {
                    return false
                }
                ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
                unwrapped.resolve() == null
            }
            is PsiMethodCallExpression -> false
            else -> extractedTarget == rawTarget
        }
    }

    private fun extractTarget(expression: PsiExpression): String {
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
        return when (expression) {
            is PsiTypeCastExpression -> expression.operand
            else -> expression
        }
    }

    private fun extractMethodCallTarget(call: PsiMethodCallExpression, fallbackExpression: PsiExpression): String {
        val methodName = call.methodExpression.referenceName
        if (methodName == "build") {
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
        val serviceClassName = resolvedClass?.qualifiedName?.let(::builderTypeToServiceName)
            ?: resolvedClass?.name?.let(::builderTypeToServiceName)
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
        val serviceName = builderClass.qualifiedName?.let(::builderTypeToServiceName)
            ?: builderClass.name?.let(::builderTypeToServiceName)
        return if (argumentTarget.isNotBlank()) {
            "$serviceName($argumentTarget)"
        } else {
            serviceName
        }
    }

    private fun builderTypeToServiceName(qualifiedOrSimpleName: String): String {
        val simpleName = qualifiedOrSimpleName.substringAfterLast('.')
        if (!simpleName.endsWith("Builder")) {
            return qualifiedOrSimpleName
        }
        val serviceSimpleName = simpleName.removeSuffix("Builder")
        val packagePrefix = qualifiedOrSimpleName.substringBeforeLast('.', missingDelimiterValue = "")
        return if (packagePrefix.isEmpty()) {
            serviceSimpleName
        } else {
            "$packagePrefix.$serviceSimpleName"
        }
    }
}
