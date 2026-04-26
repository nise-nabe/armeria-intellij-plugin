package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.util.text.StringUtil
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument

object ArmeriaKotlinRouteCollector {
    private const val ARMERIA_PACKAGE_PREFIX = "com.linecorp.armeria"
    private const val ANNOTATION_PACKAGE_PREFIX = "com.linecorp.armeria.server.annotation"
    private const val ARMERIA_HEADER_SCAN_LIMIT = 4096
    private val ARMERIA_REFERENCE_PATTERN =
        Regex("""(?<![\w"])com\.linecorp\.armeria(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")

    fun collect(file: KtFile): List<ArmeriaRoute> {
        if (!referencesArmeria(file)) {
            return emptyList()
        }
        return collectAnnotatedRoutes(file) + collectServiceRegistrations(file)
    }

    private fun referencesArmeria(file: KtFile): Boolean {
        val hasArmeriaImports = file.importDirectives.any { directive ->
            directive.importPath?.pathStr?.startsWith(ARMERIA_PACKAGE_PREFIX) == true
        }
        if (hasArmeriaImports) {
            return true
        }
        val contents = file.viewProvider.contents
        val searchWindow = contents.subSequence(0, minOf(contents.length, ARMERIA_HEADER_SCAN_LIMIT))
        return ARMERIA_REFERENCE_PATTERN.containsMatchIn(searchWindow)
    }

    private fun collectAnnotatedRoutes(file: KtFile): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                val classPrefix =
                    extractPrimaryPath(findAnnotation(file, classOrObject.annotationEntries, ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION))
                val classDecorators =
                    extractNames(findAnnotation(file, classOrObject.annotationEntries, ArmeriaRouteSupport.DECORATOR_ANNOTATION))
                val classExceptionHandlers =
                    extractNames(findAnnotation(file, classOrObject.annotationEntries, ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION))
                classOrObject.declarations.filterIsInstance<KtNamedFunction>().forEach { function ->
                    val annotation = findRouteAnnotation(file, function) ?: return@forEach
                    val paths = extractPaths(annotation.first).ifEmpty { listOf("/") }
                    val methodDecorators =
                        classDecorators + extractNames(findAnnotation(file, function.annotationEntries, ArmeriaRouteSupport.DECORATOR_ANNOTATION))
                    val methodExceptionHandlers =
                        classExceptionHandlers + extractNames(
                            findAnnotation(file, function.annotationEntries, ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION)
                        )
                    val target = buildMethodTarget(classOrObject, function)
                    for (path in paths) {
                        routes += ArmeriaRoute.create(
                            element = function,
                            kind = message("route.explorer.kind.annotatedService"),
                            protocol = ArmeriaRouteProtocol.HTTP.presentableName(),
                            httpMethod = annotation.second,
                            path = ArmeriaRouteSupport.combinePaths(classPrefix, path),
                            target = target,
                            decorators = methodDecorators.distinct(),
                            exceptionHandlers = methodExceptionHandlers.distinct(),
                        )
                    }
                }
                super.visitClassOrObject(classOrObject)
            }
        })
        return routes
    }

    private fun collectServiceRegistrations(file: KtFile): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val methodName = expression.calleeExpression?.text ?: return
                if (methodName !in setOf("service", "serviceUnder", "annotatedService")) {
                    return
                }
                if (!looksLikeArmeriaBuilderCall(expression)) {
                    return
                }
                val arguments = expression.valueArguments
                val path = extractRegistrationPath(methodName, arguments) ?: return
                val implementationExpression = extractImplementationExpression(methodName, arguments) ?: return
                val protocol = detectProtocol(implementationExpression.text)
                val target = extractTarget(implementationExpression)
                val kind = when (protocol) {
                    ArmeriaRouteProtocol.DOC_SERVICE -> message("route.explorer.kind.docService")
                    ArmeriaRouteProtocol.GRPC -> message("route.explorer.kind.grpcService")
                    ArmeriaRouteProtocol.THRIFT -> message("route.explorer.kind.thriftService")
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

    private fun findRouteAnnotation(file: KtFile, function: KtNamedFunction): Pair<KtAnnotationEntry, String>? {
        return function.annotationEntries.firstNotNullOfOrNull { candidate ->
            val qualifiedName = annotationQualifiedName(file, candidate) ?: return@firstNotNullOfOrNull null
            ArmeriaRouteSupport.routeAnnotations[qualifiedName]?.let { candidate to it }
        }
    }

    private fun findAnnotation(
        file: KtFile,
        annotations: List<KtAnnotationEntry>,
        qualifiedName: String,
    ): KtAnnotationEntry? {
        return annotations.firstOrNull { annotationMatches(file, it, qualifiedName) }
    }

    private fun annotationMatches(file: KtFile, annotation: KtAnnotationEntry, qualifiedName: String): Boolean {
        return annotationQualifiedName(file, annotation) == qualifiedName
    }

    private fun annotationQualifiedName(file: KtFile, annotation: KtAnnotationEntry): String? {
        val typeName = annotation.typeReference?.text?.removePrefix("@") ?: annotation.shortName?.asString() ?: return null
        if ('.' in typeName) {
            return typeName
        }
        file.importDirectives.forEach { directive ->
            val importPath = directive.importPath ?: return@forEach
            val importText = importPath.fqName.asString()
            if (directive.aliasName == typeName && !importPath.isAllUnder) {
                return importText
            }
            if (!importPath.isAllUnder && importText.substringAfterLast('.') == typeName) {
                return importText
            }
            if (importPath.isAllUnder) {
                val qualifiedName = "$importText.$typeName"
                if (qualifiedName.startsWith("$ANNOTATION_PACKAGE_PREFIX.")) {
                    return qualifiedName
                }
            }
        }
        return null
    }

    private fun extractPaths(annotation: KtAnnotationEntry): List<String> {
        val values = extractArgumentStrings(annotation, "value")
        if (values.isNotEmpty()) {
            return values.map(ArmeriaRouteSupport::normalizePath)
        }
        val pathValues = extractArgumentStrings(annotation, "path")
        if (pathValues.isNotEmpty()) {
            return pathValues.map(ArmeriaRouteSupport::normalizePath)
        }
        return emptyList()
    }

    private fun extractPrimaryPath(annotation: KtAnnotationEntry?): String {
        return annotation?.let(::extractPaths)?.firstOrNull().orEmpty()
    }

    private fun extractNames(annotation: KtAnnotationEntry?): List<String> {
        if (annotation == null) {
            return emptyList()
        }
        return extractArgumentExpressions(annotation, "value")
            .flatMap(::extractRenderableValues)
            .mapNotNull { it.takeIf(String::isNotBlank) }
    }

    private fun extractArgumentStrings(annotation: KtAnnotationEntry, attributeName: String): List<String> {
        return extractArgumentExpressions(annotation, attributeName).flatMap(::extractStringValues)
    }

    private fun extractArgumentExpressions(annotation: KtAnnotationEntry, attributeName: String): List<KtExpression> {
        val namedArguments = annotation.valueArguments.filter { it.getArgumentName()?.asName?.asString() == attributeName }
        val targetArguments = if (namedArguments.isNotEmpty()) {
            namedArguments
        } else if (attributeName == "value") {
            annotation.valueArguments.filter { it.getArgumentName() == null }
        } else {
            emptyList()
        }
        return targetArguments.mapNotNull { it.getArgumentExpression() }
    }

    private fun extractStringValues(expression: KtExpression): List<String> {
        return when (expression) {
            is KtCollectionLiteralExpression -> expression.innerExpressions.flatMap(::extractStringValues)
            is KtCallExpression -> extractCallExpressionValues(expression, ::extractStringValues)
            is KtStringTemplateExpression -> listOf(renderStringTemplate(expression))
            else -> listOf(expression.text).filter(String::isNotBlank)
        }
    }

    private fun extractRenderableValues(expression: KtExpression): List<String> {
        return when (expression) {
            is KtCollectionLiteralExpression -> expression.innerExpressions.flatMap(::extractRenderableValues)
            is KtCallExpression -> extractCallExpressionValues(expression, ::extractRenderableValues)
            is KtStringTemplateExpression -> listOf(renderStringTemplate(expression))
            else -> listOf(expression.text)
        }
    }

    private fun extractCallExpressionValues(
        expression: KtCallExpression,
        nestedExtractor: (KtExpression) -> List<String>,
    ): List<String> {
        return if (expression.calleeExpression?.text in setOf("arrayOf", "listOf")) {
            expression.valueArguments.mapNotNull { it.getArgumentExpression() }.flatMap(nestedExtractor)
        } else {
            listOf(expression.text).filter(String::isNotBlank)
        }
    }

    private fun renderStringTemplate(expression: KtStringTemplateExpression): String {
        return if (expression.entries.all { it is KtLiteralStringTemplateEntry }) {
            expression.entries.joinToString(separator = "") { it.text }
        } else {
            expression.text.removeSurrounding("\"")
        }
    }

    private fun looksLikeArmeriaBuilderCall(expression: KtCallExpression): Boolean {
        val qualifier = (expression.parent as? KtDotQualifiedExpression)?.receiverExpression ?: return false
        return containsArmeriaBuilderPattern(qualifier)
    }

    private fun containsArmeriaBuilderPattern(expression: KtExpression): Boolean {
        if (expression.text.contains("Server.builder()") || expression.text.contains("serverBuilder")) {
            return true
        }
        return when (expression) {
            is KtDotQualifiedExpression -> containsArmeriaBuilderPattern(expression.receiverExpression)
            is KtCallExpression -> (expression.parent as? KtDotQualifiedExpression)?.receiverExpression?.let(::containsArmeriaBuilderPattern) == true
            else -> false
        }
    }

    private fun buildMethodTarget(classOrObject: KtClassOrObject, function: KtNamedFunction): String {
        val className = classOrObject.fqName?.asString() ?: classOrObject.name ?: "<anonymous>"
        return "$className#${function.name.orEmpty()}()"
    }

    private fun extractRegistrationPath(methodName: String, arguments: List<KtValueArgument>): String? {
        return when (methodName) {
            "service", "serviceUnder" -> extractString(arguments.getOrNull(0)?.getArgumentExpression())
            "annotatedService" -> if (arguments.size > 1) {
                extractString(arguments.getOrNull(0)?.getArgumentExpression())
            } else {
                "/"
            }
            else -> null
        }
    }

    private fun extractImplementationExpression(methodName: String, arguments: List<KtValueArgument>): KtExpression? {
        return when (methodName) {
            "annotatedService" -> arguments.getOrNull(1)?.getArgumentExpression() ?: arguments.getOrNull(0)?.getArgumentExpression()
            else -> arguments.getOrNull(1)?.getArgumentExpression()
        }
    }

    private fun extractString(expression: KtExpression?): String? {
        return when (expression) {
            null -> null
            is KtStringTemplateExpression -> renderStringTemplate(expression)
            else -> expression.text.takeIf(StringUtil::isNotEmpty)
        }
    }

    private fun detectProtocol(expressionText: String): ArmeriaRouteProtocol {
        return when {
            expressionText.contains("GrpcService") -> ArmeriaRouteProtocol.GRPC
            expressionText.contains("DocService") -> ArmeriaRouteProtocol.DOC_SERVICE
            expressionText.contains("Thrift", ignoreCase = true) -> ArmeriaRouteProtocol.THRIFT
            else -> ArmeriaRouteProtocol.HTTP
        }
    }

    private fun extractTarget(expression: KtExpression): String {
        return when (expression) {
            is KtCallExpression -> expression.calleeExpression?.text ?: expression.text
            is KtDotQualifiedExpression -> expression.selectorExpression?.text ?: expression.text
            is KtNameReferenceExpression -> expression.getReferencedName()
            else -> expression.text
        }
    }
}
