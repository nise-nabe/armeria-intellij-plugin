package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType

object ArmeriaRouteSupport {
    const val ARMERIA_PACKAGE_PREFIX = "com.linecorp.armeria"
    const val ARMERIA_SPRING_PACKAGE_PREFIX = "com.linecorp.armeria.spring"
    const val ARMERIA_SERVER_PACKAGE_PREFIX = "com.linecorp.armeria.server"
    const val ARMERIA_SERVER_CLASS = "com.linecorp.armeria.server.Server"
    const val ARMERIA_SERVER_CONFIGURATOR_CLASS = "$ARMERIA_SPRING_PACKAGE_PREFIX.ArmeriaServerConfigurator"
    const val SERVER_BUILDER_CLASS = "com.linecorp.armeria.server.ServerBuilder"
    const val SPRING_BEAN_ANNOTATION = "org.springframework.context.annotation.Bean"

    val SPRING_BOOT_ARMERIA_FILE_INDICATORS =
        setOf(
            "ArmeriaServerConfigurator",
            "ArmeriaAutoConfiguration",
            "spring-boot-starter-armeria",
        )

    const val SERVER_BUILDER_SIMPLE_NAME = "ServerBuilder"
    const val ARMERIA_HEADER_SCAN_LIMIT = 4096

    const val PATH_ANNOTATION = ArmeriaRouteAnnotationSupport.PATH_ANNOTATION
    const val PATH_PREFIX_ANNOTATION = ArmeriaRouteAnnotationSupport.PATH_PREFIX_ANNOTATION
    const val DECORATOR_ANNOTATION = ArmeriaRouteAnnotationSupport.DECORATOR_ANNOTATION
    const val EXCEPTION_HANDLER_ANNOTATION = ArmeriaRouteAnnotationSupport.EXCEPTION_HANDLER_ANNOTATION
    const val BLOCKING_ANNOTATION = ArmeriaRouteAnnotationSupport.BLOCKING_ANNOTATION
    const val NON_BLOCKING_ANNOTATION = ArmeriaRouteAnnotationSupport.NON_BLOCKING_ANNOTATION

    const val GET_ANNOTATION = ArmeriaRouteAnnotationSupport.GET_ANNOTATION
    const val HEAD_ANNOTATION = ArmeriaRouteAnnotationSupport.HEAD_ANNOTATION
    const val POST_ANNOTATION = ArmeriaRouteAnnotationSupport.POST_ANNOTATION
    const val PUT_ANNOTATION = ArmeriaRouteAnnotationSupport.PUT_ANNOTATION
    const val DELETE_ANNOTATION = ArmeriaRouteAnnotationSupport.DELETE_ANNOTATION
    const val OPTIONS_ANNOTATION = ArmeriaRouteAnnotationSupport.OPTIONS_ANNOTATION
    const val PATCH_ANNOTATION = ArmeriaRouteAnnotationSupport.PATCH_ANNOTATION
    const val TRACE_ANNOTATION = ArmeriaRouteAnnotationSupport.TRACE_ANNOTATION

    val routeAnnotations = ArmeriaRouteAnnotationSupport.routeAnnotations

    fun findRouteAnnotation(method: PsiMethod): Pair<PsiAnnotation, String>? = ArmeriaRouteAnnotationSupport.findRouteAnnotation(method)

    fun extractPaths(annotation: com.intellij.psi.PsiAnnotation): List<String> = ArmeriaRouteAnnotationSupport.extractPaths(annotation)

    fun extractPrimaryPath(annotation: com.intellij.psi.PsiAnnotation?): String =
        ArmeriaRouteAnnotationSupport.extractPrimaryPath(annotation)

    fun extractNames(annotation: com.intellij.psi.PsiAnnotation?): List<String> = ArmeriaRouteAnnotationSupport.extractNames(annotation)

    fun extractStrings(value: PsiAnnotationMemberValue?): List<String> = ArmeriaRouteAnnotationSupport.extractStrings(value)

    fun renderMemberValue(value: PsiAnnotationMemberValue?): String = ArmeriaRouteAnnotationSupport.renderMemberValue(value)

    fun extractPathAnnotations(method: PsiMethod): List<String> = ArmeriaRouteAnnotationSupport.extractPathAnnotations(method)

    internal fun parsePathType(rawPath: String): Pair<PathType, String> = ArmeriaRouteAnnotationSupport.parsePathType(rawPath)

    fun combinePaths(
        prefix: String,
        path: String,
    ): String {
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

    fun normalizePath(path: String): String {
        if (path.isBlank()) {
            return "/"
        }
        val candidate = path.trim()
        return if (candidate.startsWith("/")) candidate else "/$candidate"
    }

    fun formatAnnotatedHandlerPath(
        classPrefix: String,
        rawPath: String,
    ): String {
        val (handlerPathType, handlerPath) = parsePathType(rawPath.ifBlank { "/" })
        val (prefixPathType, prefixPath) = parsePathType(classPrefix.ifBlank { "/" })
        val combinedBody = combineAnnotatedPathBodies(prefixPathType, prefixPath, handlerPathType, handlerPath)
        val displayType = annotatedPathDisplayType(prefixPathType, handlerPathType)
        return when (displayType) {
            PathType.EXACT -> combinedBody
            PathType.PREFIX -> "prefix:$combinedBody"
            PathType.REGEX -> "regex:$combinedBody"
            PathType.GLOB -> "glob:$combinedBody"
        }
    }

    private fun annotatedPathDisplayType(
        prefixPathType: PathType,
        handlerPathType: PathType,
    ): PathType {
        if (prefixPathType == PathType.REGEX || handlerPathType == PathType.REGEX) {
            return PathType.REGEX
        }
        if (handlerPathType != PathType.EXACT) {
            return handlerPathType
        }
        return prefixPathType
    }

    private fun combineAnnotatedPathBodies(
        prefixPathType: PathType,
        prefixPath: String,
        handlerPathType: PathType,
        handlerPath: String,
    ): String {
        if (prefixPath == "/" || prefixPath.isBlank()) {
            return handlerPath
        }
        if (handlerPath == "/" || handlerPath.isBlank()) {
            return prefixPath
        }
        if (prefixPathType == PathType.EXACT && handlerPathType == PathType.EXACT) {
            return combinePaths(prefixPath, handlerPath)
        }
        if (prefixPathType == PathType.REGEX || handlerPathType == PathType.REGEX) {
            return joinRegexPathBodies(prefixPath, handlerPath)
        }
        return combinePaths(prefixPath, handlerPath)
    }

    private fun joinRegexPathBodies(
        prefix: String,
        handler: String,
    ): String {
        val prefixBody = prefix.trim().removeSuffix("$")
        val trimmedHandler = handler.trim()
        val handlerHadStartAnchor = trimmedHandler.startsWith("^")
        val handlerBody = trimmedHandler.removePrefix("^")
        val combined = prefixBody + handlerBody
        return if (handlerHadStartAnchor && !prefixBody.startsWith("^")) {
            "^$combined"
        } else {
            combined
        }
    }

    fun decoratorPathPatternAppliesToRoute(
        pattern: String,
        routePath: String,
    ): Boolean {
        val normalizedPattern = normalizePath(pattern.trim().trim('"'))
        val normalizedRoute = normalizePath(routePath)
        if (normalizedPattern.endsWith("/**")) {
            val prefix = normalizedPattern.removeSuffix("/**")
            return normalizedRoute == prefix || normalizedRoute.startsWith("$prefix/")
        }
        if (normalizedPattern.endsWith("/*")) {
            val prefix = normalizedPattern.removeSuffix("/*")
            if (normalizedRoute == prefix) {
                return false
            }
            if (!normalizedRoute.startsWith("$prefix/")) {
                return false
            }
            val remainder = normalizedRoute.removePrefix("$prefix/").trimStart('/')
            return remainder.isNotEmpty() && !remainder.contains('/')
        }
        return normalizedRoute == normalizedPattern
    }

    fun isSpringBootArmeriaAvailable(
        psiFacade: JavaPsiFacade,
        scope: GlobalSearchScope,
    ): Boolean = ArmeriaServerBuilderSupport.isSpringBootArmeriaAvailable(psiFacade, scope)

    fun isArmeriaServerBeanReturnType(
        method: PsiMethod,
        scope: GlobalSearchScope,
    ): Boolean = ArmeriaServerBuilderSupport.isArmeriaServerBeanReturnType(method, scope)

    fun isArmeriaServerBeanReturnType(returnType: String): Boolean = ArmeriaServerBuilderSupport.isArmeriaServerBeanReturnType(returnType)

    fun isServerBuilderType(typeText: String): Boolean = ArmeriaServerBuilderSupport.isServerBuilderType(typeText)

    fun evaluateJavaStringConstant(variable: PsiVariable): String? = ArmeriaServerBuilderSupport.evaluateJavaStringConstant(variable)

    fun extractJavaStringConstant(expression: PsiExpression?): String? = ArmeriaServerBuilderSupport.extractJavaStringConstant(expression)

    fun referencesArmeriaInText(
        contents: CharSequence,
        scanLimit: Int = ARMERIA_HEADER_SCAN_LIMIT,
    ): Boolean = ArmeriaRouteContentScan.referencesArmeriaInText(contents, scanLimit)

    fun referencesArmeriaJavaContent(file: PsiJavaFile): Boolean {
        val hasArmeriaImports =
            file.importList
                ?.allImportStatements
                ?.any { statement ->
                    statement.importReference?.qualifiedName?.startsWith(ARMERIA_PACKAGE_PREFIX) == true
                } ?: false
        if (hasArmeriaImports) {
            return true
        }
        return referencesArmeriaInText(file.viewProvider.contents)
    }

    fun referencesArmeriaSourceContent(contents: CharSequence): Boolean = ArmeriaRouteContentScan.referencesArmeriaSourceContent(contents)

    fun referencesArmeriaKotlinContentInText(contents: CharSequence): Boolean = referencesArmeriaSourceContent(contents)

    fun mayReferenceSpringBootArmeriaInText(contents: CharSequence): Boolean =
        ArmeriaRouteContentScan.mayReferenceSpringBootArmeriaInText(contents)

    fun looksLikeServerBuilderReceiverText(text: String): Boolean = ArmeriaRouteContentScan.looksLikeServerBuilderReceiverText(text)

    fun looksLikeRouteDecoratorReceiverText(text: String): Boolean = ArmeriaRouteContentScan.looksLikeRouteDecoratorReceiverText(text)

    fun registrationKey(
        virtualFilePath: String,
        textRange: TextRange,
        methodName: String,
    ): String = "$virtualFilePath:${textRange.startOffset}:${textRange.endOffset}:$methodName"

    fun referencesArmeriaApplicationInSource(contents: CharSequence): Boolean =
        ArmeriaRouteContentScan.referencesArmeriaApplicationInSource(contents)
}
