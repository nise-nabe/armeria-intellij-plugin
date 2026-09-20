package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRoot
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRootKind
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Kotlin counterpart of [ArmeriaFileServiceRootSupport]: extracts `FileService` static roots
 * from `FileService.of(...)` / `FileService.builder(...)` and `fileService(path, ...)`.
 */
internal object ArmeriaKotlinFileServiceRootSupport {
    private const val FILE_SERVICE_CLASS = "com.linecorp.armeria.server.file.FileService"
    private const val JAVA_IO_FILE = "java.io.File"
    private val FACTORY_METHOD_NAMES = setOf("of", "builder")
    private val PATH_FACTORY_RECEIVERS = setOf("Paths", "Path")
    private val PATH_FACTORY_NAMES = setOf("get", "of")

    /** `fileService(path, ...)` registrations — [arguments] includes the mount path at index 0. */
    fun extractFromFileServiceArguments(arguments: List<KtValueArgument>): FileServiceRoot? {
        val rootExpression = arguments.getOrNull(1)?.getArgumentExpression() ?: return null
        extractFromServiceExpression(rootExpression)?.let { return it }
        fileSystemRootFromExpression(rootExpression, mutableSetOf())?.let { return it }
        if (arguments.size < 3 || !isClassPathAnchorExpression(rootExpression)) {
            return null
        }
        val pathExpression = arguments.getOrNull(2)?.getArgumentExpression() ?: return null
        val path = ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(pathExpression) ?: return null
        return FileServiceRoot(FileServiceRootKind.CLASS_PATH, path, anchorClassName(rootExpression))
    }

    /** Service expression from `service(path, FileService.of(...))` and builder chains. */
    fun extractFromServiceExpression(expression: KtExpression?): FileServiceRoot? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val call = findFileServiceFactoryCall(unwrapped, mutableSetOf()) ?: return null
        return extractFactoryArguments(call)
    }

    private fun findFileServiceFactoryCall(
        expression: KtExpression,
        visitedProperties: MutableSet<KtProperty>,
    ): KtCallExpression? {
        var current: KtExpression? = expression
        while (current != null) {
            when (current) {
                is KtDotQualifiedExpression -> {
                    val selector = current.selectorExpression
                    if (selector is KtCallExpression && isFileServiceFactorySelector(current, selector)) {
                        return selector
                    }
                    current = current.receiverExpression
                }
                is KtCallExpression -> return current.takeIf(::isFileServiceFactoryCall)
                is KtNameReferenceExpression -> {
                    current =
                        when (val resolved = current.references.firstOrNull()?.resolve()) {
                            is KtProperty -> resolved.initializer.takeIf { visitedProperties.add(resolved) }
                            else -> null
                        }
                }
                else -> current = null
            }
        }
        return null
    }

    private fun isFileServiceFactorySelector(
        qualified: KtDotQualifiedExpression,
        selector: KtCallExpression,
    ): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(selector) !in FACTORY_METHOD_NAMES) {
            return false
        }
        if (isFileServiceFactoryCall(selector)) {
            return true
        }
        val receiver = qualified.receiverExpression as? KtNameReferenceExpression
        if (receiver?.getReferencedName() == "FileService") {
            return true
        }
        val resolved = receiver?.references?.firstOrNull()?.resolve() as? PsiClass
        return resolved?.qualifiedName == FILE_SERVICE_CLASS
    }

    private fun isFileServiceFactoryCall(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinExpressionSupport.resolveCallName(call) !in FACTORY_METHOD_NAMES) {
            return false
        }
        val callee = call.calleeExpression as? KtNameReferenceExpression ?: return false
        val resolved = callee.references.firstOrNull()?.resolve() as? PsiMethod ?: return false
        return resolved.containingClass?.qualifiedName == FILE_SERVICE_CLASS
    }

    private fun extractFactoryArguments(call: KtCallExpression): FileServiceRoot? {
        val arguments = call.valueArguments
        return when (arguments.size) {
            1 -> fileSystemRootFromExpression(arguments[0].getArgumentExpression(), mutableSetOf())
            2 -> {
                val path =
                    ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(arguments[1].getArgumentExpression())
                        ?: return null
                FileServiceRoot(FileServiceRootKind.CLASS_PATH, path, anchorClassName(arguments[0].getArgumentExpression()))
            }
            else -> null
        }
    }

    private fun fileSystemRootFromExpression(
        expression: KtExpression?,
        visitedProperties: MutableSet<KtProperty>,
    ): FileServiceRoot? {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        val path =
            when (unwrapped) {
                is KtCallExpression -> pathFromFileConstructor(unwrapped, visitedProperties)
                is KtDotQualifiedExpression -> pathFromPathFactoryCall(unwrapped, visitedProperties)
                is KtNameReferenceExpression -> {
                    when (val resolved = unwrapped.references.firstOrNull()?.resolve()) {
                        is KtProperty -> {
                            if (!visitedProperties.add(resolved)) {
                                return null
                            }
                            return fileSystemRootFromExpression(resolved.initializer, visitedProperties)
                        }
                        is PsiVariable -> ArmeriaRouteSupport.evaluateJavaStringConstant(resolved)
                        else -> null
                    }
                }
                else -> ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(unwrapped)
            } ?: return null
        return FileServiceRoot(FileServiceRootKind.FILE_SYSTEM, path)
    }

    private fun pathFromFileConstructor(
        call: KtCallExpression,
        visitedProperties: MutableSet<KtProperty>,
    ): String? {
        val callee = call.calleeExpression as? KtNameReferenceExpression ?: return null
        if (callee.getReferencedName() != "File") {
            return null
        }
        val resolved = callee.references.firstOrNull()?.resolve() as? PsiMethod
        if (resolved != null && (!resolved.isConstructor || resolved.containingClass?.qualifiedName != JAVA_IO_FILE)) {
            return null
        }
        val arguments = call.valueArguments
        return when (arguments.size) {
            1 -> ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(arguments[0].getArgumentExpression())
            2 -> {
                val parent =
                    fileSystemRootFromExpression(arguments[0].getArgumentExpression(), visitedProperties)?.path
                        ?: ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(arguments[0].getArgumentExpression())
                        ?: return null
                val child =
                    ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(arguments[1].getArgumentExpression())
                        ?: return null
                "$parent/$child"
            }
            else -> null
        }
    }

    private fun pathFromPathFactoryCall(
        qualified: KtDotQualifiedExpression,
        visitedProperties: MutableSet<KtProperty>,
    ): String? {
        val selector = qualified.selectorExpression as? KtCallExpression ?: return null
        if (qualified.receiverExpression.text.substringAfterLast('.') !in PATH_FACTORY_RECEIVERS) {
            return null
        }
        if (ArmeriaKotlinExpressionSupport.resolveCallName(selector) !in PATH_FACTORY_NAMES) {
            return null
        }
        val parts =
            selector.valueArguments.map { argument ->
                fileSystemRootFromExpression(argument.getArgumentExpression(), visitedProperties)?.path
                    ?: ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(argument.getArgumentExpression())
                    ?: return null
            }
        return parts.joinToString("/")
    }

    private fun isClassPathAnchorExpression(expression: KtExpression): Boolean {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return false
        if (findClassLiteral(unwrapped) != null) {
            return true
        }
        if (unwrapped is KtDotQualifiedExpression &&
            unwrapped.selectorExpression?.text?.substringBefore('(') in CLASS_LOADER_SELECTOR_NAMES
        ) {
            return true
        }
        val resolved =
            (unwrapped as? KtNameReferenceExpression)?.references?.firstOrNull()?.resolve() ?: return false
        return when (resolved) {
            is KtProperty ->
                resolved.typeReference
                    ?.text
                    ?.substringAfterLast('.')
                    ?.startsWith("ClassLoader") == true
            is PsiVariable -> resolved.type.canonicalText == "java.lang.ClassLoader"
            else -> false
        }
    }

    private fun anchorClassName(expression: KtExpression?): String {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return ""
        val classLiteral =
            when (unwrapped) {
                is KtClassLiteralExpression -> unwrapped
                is KtDotQualifiedExpression -> {
                    val selector = unwrapped.selectorExpression?.text?.substringBefore('(')
                    if (selector in JAVA_CLASS_SELECTOR_NAMES) findClassLiteral(unwrapped) else null
                }
                else -> null
            } ?: return ""
        val reference = classLiteral.receiverExpression as? KtNameReferenceExpression ?: return ""
        return when (val resolved = reference.references.firstOrNull()?.resolve()) {
            is KtClass -> resolved.fqName?.asString()
            is PsiClass -> resolved.qualifiedName
            else -> null
        } ?: ""
    }

    private fun findClassLiteral(expression: KtExpression): KtClassLiteralExpression? {
        var current: KtExpression? = expression
        while (current != null) {
            current =
                when (current) {
                    is KtClassLiteralExpression -> return current
                    is KtDotQualifiedExpression -> current.receiverExpression
                    else -> null
                }
        }
        return null
    }

    private val CLASS_LOADER_SELECTOR_NAMES = setOf("classLoader", "getClassLoader")
    private val JAVA_CLASS_SELECTOR_NAMES = setOf("java", "javaClass", "kotlin")
}
