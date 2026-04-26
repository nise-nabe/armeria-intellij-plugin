package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
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
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

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
    private const val ARMERIA_HEADER_SCAN_LIMIT = 4096
    private val ARMERIA_REFERENCE_PATTERN =
        Regex("""(?<![\w"])com\.linecorp\.armeria(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")

    fun collect(project: Project): List<ArmeriaRoute> {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            val routes = mutableListOf<ArmeriaRoute>()
            val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            for (virtualFile in javaFiles) {
                val psiFile =
                    PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
                if (!referencesArmeria(psiFile)) {
                    continue
                }
                routes += collectAnnotatedRoutes(psiFile)
                routes += collectServiceRegistrations(psiFile)
            }
            val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            for (virtualFile in kotlinFiles) {
                val psiFile =
                    PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
                routes += ArmeriaKotlinRouteCollector.collect(psiFile)
            }
            CachedValueProvider.Result.create(
                routes.sortedWith(compareBy(ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target)),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
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

    private fun collectAnnotatedRoutes(file: PsiJavaFile): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val classPrefix =
                    ArmeriaRouteSupport.extractPrimaryPath(aClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION))
                val classDecorators =
                    ArmeriaRouteSupport.extractNames(aClass.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
                val classExceptionHandlers =
                    ArmeriaRouteSupport.extractNames(aClass.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION))
                for (method in aClass.methods) {
                    val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: continue
                    val paths = ArmeriaRouteSupport.extractPaths(annotation.first).ifEmpty { listOf("/") }
                    val methodDecorators =
                        classDecorators + ArmeriaRouteSupport.extractNames(method.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
                    val methodExceptionHandlers = classExceptionHandlers + ArmeriaRouteSupport.extractNames(
                        method.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION)
                    )
                    val target = buildMethodTarget(aClass, method)
                    for (path in paths) {
                        routes += ArmeriaRoute.create(
                            element = method,
                            kind = message("route.explorer.kind.annotatedService"),
                            protocol = RouteProtocol.HTTP.presentableName(),
                            httpMethod = annotation.second,
                            path = ArmeriaRouteSupport.combinePaths(classPrefix, path),
                            target = target,
                            decorators = methodDecorators.distinct(),
                            exceptionHandlers = methodExceptionHandlers.distinct(),
                        )
                    }
                }
                super.visitClass(aClass)
            }
        })
        return routes
    }

    private fun collectServiceRegistrations(file: PsiFile): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                val methodName = expression.methodExpression.referenceName ?: return
                if (methodName !in setOf("service", "serviceUnder", "annotatedService")) {
                    return
                }
                if (!looksLikeArmeriaBuilderCall(expression)) {
                    return
                }
                val arguments = expression.argumentList.expressions
                val path = extractRegistrationPath(methodName, arguments) ?: return
                val implementationExpression = when (methodName) {
                    "annotatedService" -> arguments.getOrNull(1) ?: arguments.getOrNull(0)
                    else -> arguments.getOrNull(1)
                } ?: return
                val protocol = detectProtocol(implementationExpression.text)
                val target = extractTarget(implementationExpression)
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
                    httpMethod = if (methodName == "serviceUnder") "UNDER" else "ANY",
                    path = ArmeriaRouteSupport.normalizePath(path),
                    target = target,
                )
            }
        })
        return routes
    }

    private fun looksLikeArmeriaBuilderCall(expression: PsiMethodCallExpression): Boolean {
        val resolvedClass = expression.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass?.startsWith("com.linecorp.armeria.server") == true) {
            return true
        }
        val qualifierText = expression.methodExpression.qualifierExpression?.text ?: return false
        return qualifierText.contains("Server.builder()") || qualifierText.contains("serverBuilder")
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

    private fun extractTarget(expression: PsiExpression): String {
        val unwrapped = when (expression) {
            is PsiTypeCastExpression -> expression.operand
            else -> expression
        } ?: return expression.text
        return when (unwrapped) {
            is PsiNewExpression -> {
                val classReference = unwrapped.classReference?.qualifiedName ?: unwrapped.classReference?.referenceName
                classReference ?: expression.text
            }

            is PsiMethodCallExpression -> unwrapped.methodExpression.referenceName ?: expression.text
            is PsiReferenceExpression -> {
                when (val resolved = unwrapped.resolve()) {
                    is PsiVariable -> resolved.type.presentableText
                    is PsiClass -> resolved.qualifiedName ?: resolved.name ?: expression.text
                    else -> unwrapped.text
                }
            }

            else -> expression.text
        }
    }
}
