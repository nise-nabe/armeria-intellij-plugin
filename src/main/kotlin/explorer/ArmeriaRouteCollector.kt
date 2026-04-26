package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

object ArmeriaRouteCollector {
    private const val armeriaPackagePrefix = "com.linecorp.armeria"
    private const val armeriaReferenceScanLimit = 4096

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
            CachedValueProvider.Result.create(
                routes.sortedWith(compareBy(ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target)),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }

    private fun referencesArmeria(file: PsiJavaFile): Boolean {
        val importsArmeria = file.importList
            ?.allImportStatements
            ?.any { statement ->
                statement.importReference?.qualifiedName?.startsWith(armeriaPackagePrefix) == true
            } == true
        val contents = file.viewProvider.contents
        val searchWindow = contents.subSequence(0, minOf(contents.length, armeriaReferenceScanLimit))
        return importsArmeria || searchWindow.indexOf(armeriaPackagePrefix) >= 0
    }

    private fun collectAnnotatedRoutes(file: PsiJavaFile): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val classPrefix =
                    ArmeriaRouteSupport.extractPrimaryPath(aClass.getAnnotation(ArmeriaRouteSupport.pathPrefixAnnotation))
                val classDecorators =
                    ArmeriaRouteSupport.extractNames(aClass.getAnnotation(ArmeriaRouteSupport.decoratorAnnotation))
                val classExceptionHandlers =
                    ArmeriaRouteSupport.extractNames(aClass.getAnnotation(ArmeriaRouteSupport.exceptionHandlerAnnotation))
                for (method in aClass.methods) {
                    val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: continue
                    val paths = ArmeriaRouteSupport.extractPaths(annotation.first).ifEmpty { listOf("/") }
                    val methodDecorators =
                        classDecorators + ArmeriaRouteSupport.extractNames(method.getAnnotation(ArmeriaRouteSupport.decoratorAnnotation))
                    val methodExceptionHandlers = classExceptionHandlers + ArmeriaRouteSupport.extractNames(
                        method.getAnnotation(ArmeriaRouteSupport.exceptionHandlerAnnotation)
                    )
                    val target = buildMethodTarget(aClass, method)
                    for (path in paths) {
                        routes += ArmeriaRoute.create(
                            element = method,
                            kind = "Annotated service",
                            protocol = "HTTP",
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
                    "DocService" -> "Doc service"
                    "gRPC" -> "gRPC service"
                    "Thrift" -> "Thrift service"
                    else -> if (methodName == "annotatedService") "Annotated service registration" else "Service registration"
                }
                routes += ArmeriaRoute.create(
                    element = expression,
                    kind = kind,
                    protocol = protocol,
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

    private fun extractRegistrationPath(methodName: String, arguments: Array<PsiExpression>): String? {
        return when (methodName) {
            "service", "serviceUnder" -> extractString(arguments.getOrNull(0))
            "annotatedService" -> if (arguments.size > 1) extractString(arguments.getOrNull(0)) else "/"
            else -> null
        }
    }

    private fun extractString(expression: PsiExpression?): String? {
        return when (expression) {
            null -> null
            is PsiLiteralExpression -> expression.value as? String
            else -> expression.text.takeIf { StringUtil.isNotEmpty(it) }
        }
    }

    private fun detectProtocol(expressionText: String): String {
        return when {
            expressionText.contains("GrpcService") -> "gRPC"
            expressionText.contains("DocService") -> "DocService"
            expressionText.contains("Thrift", ignoreCase = true) -> "Thrift"
            else -> "HTTP"
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
