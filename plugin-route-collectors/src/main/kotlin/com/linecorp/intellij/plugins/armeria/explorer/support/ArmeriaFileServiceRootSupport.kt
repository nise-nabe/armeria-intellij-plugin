package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRoot
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRootKind

/**
 * Extracts the static root declaration from `FileService` factories (`of` / `builder`) and
 * `fileService(path, ...)` server-builder registrations.
 *
 * Resolves values rather than stringifying PSI: string literals and compile-time constants
 * only; unresolved expressions produce no [FileServiceRoot].
 */
internal object ArmeriaFileServiceRootSupport {
    private const val FILE_SERVICE_CLASS = "com.linecorp.armeria.server.file.FileService"
    private const val JAVA_IO_FILE = "java.io.File"
    private const val JAVA_NIO_PATH = "java.nio.file.Path"
    private const val JAVA_NIO_PATHS = "java.nio.file.Paths"
    private val FACTORY_METHOD_NAMES = setOf("of", "builder")
    private val PATH_FACTORY_QUALIFIERS = setOf(JAVA_NIO_PATHS, JAVA_NIO_PATH, "Paths", "Path")

    /**
     * `fileService(path, root)` registrations — [arguments] includes the mount path at index 0.
     */
    fun extractFromFileServiceCall(arguments: Array<PsiExpression>): FileServiceRoot? {
        val rootExpression = arguments.getOrNull(1) ?: return null
        extractFromServiceExpression(rootExpression)?.let { return it }
        return fileSystemRootFromExpression(rootExpression, mutableSetOf())
    }

    /**
     * Service implementation expression from `service(path, FileService.of(...))` or
     * `fileService(path, FileService.builder(...).build())`: walks qualifier chains and
     * variable initializers until a `FileService` `of`/`builder` factory call is found.
     */
    fun extractFromServiceExpression(expression: PsiExpression?): FileServiceRoot? =
        extractFromServiceExpression(expression, mutableSetOf())

    private fun extractFromServiceExpression(
        expression: PsiExpression?,
        visitedVariables: MutableSet<PsiVariable>,
    ): FileServiceRoot? {
        val unwrapped = unwrap(expression) ?: return null
        val call =
            when (unwrapped) {
                is PsiMethodCallExpression -> unwrapped
                is PsiReferenceExpression -> {
                    val variable = unwrapped.resolve() as? PsiVariable ?: return null
                    if (!visitedVariables.add(variable)) {
                        return null
                    }
                    return extractFromServiceExpression(variable.initializer, visitedVariables)
                }
                else -> return null
            }
        var current: PsiMethodCallExpression = call
        while (true) {
            if (isFileServiceFactoryCall(current)) {
                return extractFactoryArguments(current)
            }
            current =
                when (val qualifier = current.methodExpression.qualifierExpression) {
                    is PsiMethodCallExpression -> qualifier
                    is PsiReferenceExpression -> {
                        val variable = qualifier.resolve() as? PsiVariable ?: return null
                        if (!visitedVariables.add(variable)) {
                            return null
                        }
                        unwrap(variable.initializer) as? PsiMethodCallExpression ?: return null
                    }
                    else -> return null
                }
        }
    }

    private fun isFileServiceFactoryCall(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName !in FACTORY_METHOD_NAMES) {
            return false
        }
        val containingClass = call.resolveMethod()?.containingClass
        if (containingClass != null) {
            return containingClass.qualifiedName == FILE_SERVICE_CLASS
        }
        return call.methodExpression.qualifierExpression
            ?.text
            ?.substringAfterLast('.') == "FileService"
    }

    private fun extractFactoryArguments(call: PsiMethodCallExpression): FileServiceRoot? {
        val arguments = call.argumentList.expressions
        return when (arguments.size) {
            1 -> fileSystemRootFromExpression(arguments[0], mutableSetOf())
            2 -> {
                val path = ArmeriaRouteSupport.extractJavaStringConstant(arguments[1]) ?: return null
                FileServiceRoot(
                    FileServiceRootKind.CLASS_PATH,
                    path,
                    anchorClassName(arguments[0]),
                    anchorPackageName(arguments[0]),
                )
            }
            else -> null
        }
    }

    /**
     * File-system root candidates: `new File(...)`, `Paths.get(...)`, `Path.of(...)`,
     * string literals/constants, and variables holding one of those.
     */
    private fun fileSystemRootFromExpression(
        expression: PsiExpression?,
        visitedVariables: MutableSet<PsiVariable>,
    ): FileServiceRoot? {
        val unwrapped = unwrap(expression) ?: return null
        val path =
            when (unwrapped) {
                is PsiNewExpression -> pathFromNewExpression(unwrapped, visitedVariables)
                is PsiMethodCallExpression -> pathFromPathFactoryCall(unwrapped, visitedVariables)
                is PsiReferenceExpression -> {
                    val variable = unwrapped.resolve() as? PsiVariable ?: return null
                    if (!visitedVariables.add(variable)) {
                        return null
                    }
                    return fileSystemRootFromExpression(variable.initializer, visitedVariables)
                }
                else -> ArmeriaRouteSupport.extractJavaStringConstant(unwrapped)
            } ?: return null
        return FileServiceRoot(FileServiceRootKind.FILE_SYSTEM, path)
    }

    private fun pathFromNewExpression(
        expression: PsiNewExpression,
        visitedVariables: MutableSet<PsiVariable>,
    ): String? {
        val classReference = expression.classReference ?: return null
        val resolvedName = (classReference.resolve() as? PsiClass)?.qualifiedName
        val isFileType =
            resolvedName == JAVA_IO_FILE ||
                (
                    resolvedName == null &&
                        (
                            classReference.qualifiedName == JAVA_IO_FILE ||
                                (classReference.qualifier == null && classReference.referenceName == "File")
                        )
                )
        if (!isFileType) {
            return null
        }
        val arguments = expression.argumentList?.expressions ?: return null
        return when (arguments.size) {
            1 -> ArmeriaRouteSupport.extractJavaStringConstant(arguments[0])
            2 -> {
                val parent = parentPath(arguments[0], visitedVariables) ?: return null
                val child = ArmeriaRouteSupport.extractJavaStringConstant(arguments[1]) ?: return null
                "$parent/$child"
            }
            else -> null
        }
    }

    private fun parentPath(
        expression: PsiExpression,
        visitedVariables: MutableSet<PsiVariable>,
    ): String? {
        fileSystemRootFromExpression(expression, visitedVariables)?.let { return it.path }
        return ArmeriaRouteSupport.extractJavaStringConstant(expression)
    }

    private fun pathFromPathFactoryCall(
        call: PsiMethodCallExpression,
        visitedVariables: MutableSet<PsiVariable>,
    ): String? {
        if (call.methodExpression.referenceName !in setOf("get", "of")) {
            return null
        }
        val qualifier = call.methodExpression.qualifierExpression
        val qualifierClass =
            (qualifier as? PsiReferenceExpression)?.let { it.resolve() as? PsiClass }?.qualifiedName
                ?: qualifier?.text?.substringBefore('(')?.substringAfterLast('.')
        if (qualifierClass !in PATH_FACTORY_QUALIFIERS) {
            return null
        }
        val parts =
            call.argumentList.expressions.map { argument ->
                fileSystemRootFromExpression(argument, visitedVariables)?.path
                    ?: ArmeriaRouteSupport.extractJavaStringConstant(argument)
                    ?: return null
            }
        return parts.joinToString("/")
    }

    private fun anchorClass(expression: PsiExpression): PsiClass? {
        val unwrapped = unwrap(expression) as? PsiClassObjectAccessExpression ?: return null
        return (unwrapped.operand.type as? PsiClassType)?.resolve()
    }

    private fun anchorClassName(expression: PsiExpression): String {
        val unwrapped = unwrap(expression) as? PsiClassObjectAccessExpression ?: return ""
        val type = unwrapped.operand.type as? PsiClassType ?: return ""
        return type.resolve()?.qualifiedName ?: type.canonicalText.substringBefore('<')
    }

    private fun anchorPackageName(expression: PsiExpression): String = anchorClass(expression)?.let(PsiUtil::getPackageName) ?: ""

    private fun unwrap(expression: PsiExpression?): PsiExpression? {
        var current = expression ?: return null
        while (true) {
            current =
                when (current) {
                    is PsiTypeCastExpression -> current.operand ?: return null
                    is PsiParenthesizedExpression -> current.expression ?: return null
                    else -> return current
                }
        }
    }
}
