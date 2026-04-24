package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationArrayInitializerMemberValue
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

object ArmeriaRouteCollector {
    private val routeAnnotations = mapOf(
        "com.linecorp.armeria.server.annotation.Get" to "GET",
        "com.linecorp.armeria.server.annotation.Head" to "HEAD",
        "com.linecorp.armeria.server.annotation.Post" to "POST",
        "com.linecorp.armeria.server.annotation.Put" to "PUT",
        "com.linecorp.armeria.server.annotation.Delete" to "DELETE",
        "com.linecorp.armeria.server.annotation.Options" to "OPTIONS",
        "com.linecorp.armeria.server.annotation.Patch" to "PATCH",
        "com.linecorp.armeria.server.annotation.Trace" to "TRACE",
    )

    private const val pathPrefixAnnotation = "com.linecorp.armeria.server.annotation.PathPrefix"
    private const val decoratorAnnotation = "com.linecorp.armeria.server.annotation.Decorator"
    private const val exceptionHandlerAnnotation = "com.linecorp.armeria.server.annotation.ExceptionHandler"

    fun collect(project: Project): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        for (virtualFile in javaFiles) {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            routes += collectAnnotatedRoutes(psiFile)
            routes += collectServiceRegistrations(psiFile)
        }
        return routes.sortedWith(compareBy(ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target))
    }

    private fun collectAnnotatedRoutes(file: PsiJavaFile): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val classPrefix = extractPrimaryPath(aClass.getAnnotation(pathPrefixAnnotation))
                val classDecorators = extractNames(aClass.getAnnotation(decoratorAnnotation))
                val classExceptionHandlers = extractNames(aClass.getAnnotation(exceptionHandlerAnnotation))
                for (method in aClass.methods) {
                    val annotation = method.modifierList.annotations.firstNotNullOfOrNull { candidate ->
                        val qualifiedName = candidate.qualifiedName ?: return@firstNotNullOfOrNull null
                        routeAnnotations[qualifiedName]?.let { candidate to it }
                    } ?: continue
                    val paths = extractPaths(annotation.first).ifEmpty { listOf("/") }
                    val methodDecorators = classDecorators + extractNames(method.getAnnotation(decoratorAnnotation))
                    val methodExceptionHandlers = classExceptionHandlers + extractNames(method.getAnnotation(exceptionHandlerAnnotation))
                    val target = buildMethodTarget(aClass, method)
                    for (path in paths) {
                        routes += ArmeriaRoute.create(
                            element = method,
                            kind = "Annotated service",
                            protocol = "HTTP",
                            httpMethod = annotation.second,
                            path = combinePaths(classPrefix, path),
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
                val path = when (methodName) {
                    "service", "serviceUnder" -> extractString(arguments.getOrNull(0)) ?: return
                    "annotatedService" -> if (arguments.size > 1) {
                        extractString(arguments.getOrNull(0)) ?: return
                    } else {
                        "/"
                    }
                    else -> return
                }
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
                    path = normalizePath(path),
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

    private fun extractPaths(annotation: PsiAnnotation): List<String> {
        val values = extractStrings(annotation.findDeclaredAttributeValue("value"))
        if (values.isNotEmpty()) {
            return values.map(::normalizePath)
        }
        val pathValues = extractStrings(annotation.findDeclaredAttributeValue("path"))
        if (pathValues.isNotEmpty()) {
            return pathValues.map(::normalizePath)
        }
        return emptyList()
    }

    private fun extractPrimaryPath(annotation: PsiAnnotation?): String {
        if (annotation == null) {
            return ""
        }
        return extractPaths(annotation).firstOrNull().orEmpty()
    }

    private fun extractNames(annotation: PsiAnnotation?): List<String> {
        if (annotation == null) {
            return emptyList()
        }
        return extractStrings(annotation.findDeclaredAttributeValue("value"))
            .ifEmpty { listOf(renderMemberValue(annotation.findDeclaredAttributeValue("value"))) }
            .mapNotNull { it.takeIf(String::isNotBlank) }
    }

    private fun extractStrings(value: PsiAnnotationMemberValue?): List<String> {
        return when (value) {
            null -> emptyList()
            is PsiLiteralExpression -> listOfNotNull(value.value as? String)
            is PsiAnnotationArrayInitializerMemberValue -> value.initializers.flatMap(::extractStrings)
            else -> listOf(renderMemberValue(value)).filter(String::isNotBlank)
        }
    }

    private fun renderMemberValue(value: PsiAnnotationMemberValue?): String {
        return value?.text?.removePrefix("\"")?.removeSuffix("\"").orEmpty()
    }

    private fun combinePaths(prefix: String, path: String): String {
        val normalizedPrefix = normalizePath(prefix)
        val normalizedPath = normalizePath(path)
        if (normalizedPrefix == "/") {
            return normalizedPath
        }
        if (normalizedPath == "/") {
            return normalizedPrefix
        }
        return "${normalizedPrefix.removeSuffix("/")}/${normalizedPath.removePrefix("/")}"
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) {
            return "/"
        }
        val candidate = path.trim()
        return if (candidate.startsWith("/")) candidate else "/$candidate"
    }

    private fun buildMethodTarget(psiClass: PsiClass, method: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
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
            is com.intellij.psi.PsiNewExpression -> {
                val classReference = unwrapped.classReference?.qualifiedName ?: unwrapped.classReference?.referenceName
                classReference ?: expression.text
            }
            is PsiMethodCallExpression -> unwrapped.methodExpression.referenceName ?: expression.text
            is PsiReferenceExpression -> {
                val resolved = unwrapped.resolve()
                when (resolved) {
                    is PsiVariable -> resolved.type.presentableText
                    is PsiClass -> resolved.qualifiedName ?: resolved.name ?: expression.text
                    else -> unwrapped.text
                }
            }
            else -> expression.text
        }
    }
}
