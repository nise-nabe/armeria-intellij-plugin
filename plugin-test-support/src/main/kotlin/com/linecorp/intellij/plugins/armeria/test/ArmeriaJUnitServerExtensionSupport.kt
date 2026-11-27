package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport

/**
 * Java/Core test-support helpers.
 *
 * This object is reachable from always-registered Java extension points, so it must not
 * reference Kotlin plugin PSI types. Kotlin-specific behavior lives in
 * [ArmeriaKotlinJUnitServerExtensionSupport] behind plugin-availability guards.
 */
internal object ArmeriaJUnitServerExtensionSupport {
    const val REGISTER_EXTENSION_ANNOTATION = "org.junit.jupiter.api.extension.RegisterExtension"
    const val REGISTER_EXTENSION_ANNOTATION_SHORT = "RegisterExtension"
    const val SERVER_EXTENSION_CLASS = "com.linecorp.armeria.testing.junit5.server.ServerExtension"
    const val WEB_CLIENT_CLASS = "com.linecorp.armeria.client.WebClient"
    const val BLOCKING_WEB_CLIENT_CLASS = "com.linecorp.armeria.client.blocking.BlockingWebClient"

    fun hasRegisterExtensionAnnotation(owner: PsiModifierListOwner): Boolean = owner.annotations.any(::isRegisterExtensionAnnotation)

    private fun isRegisterExtensionAnnotation(annotation: PsiAnnotation): Boolean {
        annotation.qualifiedName?.let {
            if (it == REGISTER_EXTENSION_ANNOTATION) {
                return true
            }
        }
        val shortName = annotation.nameReferenceElement?.referenceName ?: return false
        if (shortName != REGISTER_EXTENSION_ANNOTATION_SHORT) {
            return false
        }
        when (val resolved = annotation.nameReferenceElement?.resolve()) {
            is PsiClass -> {
                return resolved.qualifiedName == REGISTER_EXTENSION_ANNOTATION
            }
        }
        val javaFile = annotation.containingFile as? PsiJavaFile ?: return false
        return javaFile.importList?.importStatements?.any { import ->
            when {
                import.isOnDemand -> {
                    import.qualifiedName?.let { packageName ->
                        REGISTER_EXTENSION_ANNOTATION == "$packageName.$REGISTER_EXTENSION_ANNOTATION_SHORT"
                    } == true
                }

                else -> {
                    import.qualifiedName == REGISTER_EXTENSION_ANNOTATION ||
                        import.qualifiedName?.endsWith(".$REGISTER_EXTENSION_ANNOTATION_SHORT") == true
                }
            }
        } == true
    }

    fun isServerExtensionType(
        type: PsiType?,
        project: Project,
    ): Boolean {
        val resolvedClass = (type as? PsiClassType)?.resolve() ?: return false
        return isServerExtensionClass(resolvedClass, project)
    }

    fun isServerExtensionClass(
        psiClass: PsiClass,
        project: Project,
    ): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (qualifiedName == SERVER_EXTENSION_CLASS) {
            return true
        }
        val baseClass =
            JavaPsiFacade.getInstance(project).findClass(SERVER_EXTENSION_CLASS, GlobalSearchScope.allScope(project)) ?: return false
        return psiClass.isInheritor(baseClass, true)
    }

    fun serverExtensionFromField(field: PsiField): ArmeriaJUnitServerExtension? {
        if (!hasRegisterExtensionAnnotation(field) || !isServerExtensionType(field.type, field.project)) {
            return null
        }
        val containingClass = field.containingClass ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = field,
            variableName = field.name,
            containingClassName = containingClass.qualifiedName.orEmpty(),
            moduleName = ArmeriaTestMetadata.moduleName(field),
        )
    }

    fun serverExtensionFromMethod(method: PsiMethod): ArmeriaJUnitServerExtension? {
        if (method.parameterList.parametersCount > 0) {
            return null
        }
        if (!hasRegisterExtensionAnnotation(method) || !isServerExtensionType(method.returnType, method.project)) {
            return null
        }
        val containingClass = method.containingClass ?: return null
        val containingClassName = containingClass.qualifiedName ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = method,
            variableName = method.name,
            containingClassName = containingClassName,
            moduleName = ArmeriaTestMetadata.moduleName(method),
            isFactoryMethod = true,
        )
    }

    fun serverExtensionsInClass(psiClass: PsiClass): List<ArmeriaJUnitServerExtension> {
        if (isKotlinBackedClass(psiClass)) {
            return emptyList()
        }
        return psiClass.fields.mapNotNull(::serverExtensionFromField) +
            psiClass.methods.mapNotNull(::serverExtensionFromMethod)
    }

    private fun isKotlinBackedClass(psiClass: PsiClass): Boolean {
        val containingFile = psiClass.containingFile ?: return false
        val extension = containingFile.virtualFile?.extension ?: containingFile.name.substringAfterLast('.', "")
        return extension.equals("kt", ignoreCase = true) || extension.equals("kts", ignoreCase = true)
    }

    fun fileMayContainRegisterExtension(fileText: String): Boolean =
        REGISTER_EXTENSION_ANNOTATION in fileText || "@$REGISTER_EXTENSION_ANNOTATION_SHORT" in fileText

    /** True when [file] is under a test source root (IDE markers only; filename is not considered). */
    fun isInTestSourceContent(file: PsiFile): Boolean {
        val virtualFile = file.virtualFile ?: return false
        return isInTestSourceContent(virtualFile, file.project)
    }

    /** True when [virtualFile] is under a test source root. */
    fun isInTestSourceContent(
        virtualFile: VirtualFile,
        project: Project,
    ): Boolean {
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        return fileIndex.isInTestSourceContent(virtualFile) ||
            TestSourcesFilter.isTestSources(virtualFile, project)
    }

    fun escapeStringLiteral(value: String): String =
        buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '$' -> append("\\$")
                    else -> append(character)
                }
            }
        }

    fun escapeKotlinStringLiteral(value: String): String =
        buildString(value.length) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    else -> append(character)
                }
            }
        }

    fun enclosingTestClassName(element: PsiElement): String? = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName

    fun enclosingServerExtension(
        element: PsiElement,
        scope: GlobalSearchScope,
        cachedExtensions: MutableMap<String, List<ArmeriaJUnitServerExtension>>? = null,
    ): ArmeriaJUnitServerExtension? {
        val className = enclosingTestClassName(element) ?: return null
        val project = element.project
        val extensions =
            cachedExtensions?.getOrPut(className) {
                serverExtensionsAccessibleFrom(className, project, scope)
            } ?: serverExtensionsAccessibleFrom(className, project, scope)
        if (extensions.isEmpty()) {
            return null
        }
        return resolveScopedExtension(extensions)
    }

    fun serverExtensionsAccessibleFrom(
        testClassName: String,
        project: Project,
        scope: GlobalSearchScope,
    ): List<ArmeriaJUnitServerExtension> {
        val testClass = JavaPsiFacade.getInstance(project).findClass(testClassName, scope)
        if (testClass != null) {
            return ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, testClass)
        }
        return ArmeriaJUnitServerExtensionCollector
            .collect(project)
            .filter { extension -> canAccessServerExtension(testClassName, extension.containingClassName, project, scope) }
    }

    internal fun canAccessServerExtension(
        testClassName: String,
        extensionClassName: String,
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean {
        if (isEnclosingOrNestedTestClass(testClassName, extensionClassName)) {
            return true
        }
        if (extensionClassName.isEmpty()) {
            return false
        }
        val testClass = JavaPsiFacade.getInstance(project).findClass(testClassName, scope)
        val extensionClass = JavaPsiFacade.getInstance(project).findClass(extensionClassName, scope)
        if (testClass != null && extensionClass != null && testClass.isInheritor(extensionClass, true)) {
            return true
        }
        if (!ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
            return false
        }
        return extensionClassName in ArmeriaKotlinJUnitServerExtensionSupport.kotlinSuperTypeNames(testClassName, project, scope)
    }

    private fun isEnclosingOrNestedTestClass(
        testClassName: String,
        extensionClassName: String,
    ): Boolean =
        testClassName == extensionClassName ||
            testClassName.startsWith("$extensionClassName.") ||
            testClassName.startsWith("$extensionClassName$")

    fun matchesServerReceiver(
        expression: PsiElement,
        serverVariableName: String,
    ): Boolean =
        when (expression) {
            is PsiReferenceExpression -> {
                expression.referenceName == serverVariableName
            }

            is PsiMethodCallExpression -> {
                expression.methodExpression.referenceName == serverVariableName &&
                    expression.argumentList.expressionCount == 0 &&
                    isValidFactoryQualifier(expression.methodExpression.qualifierExpression, expression)
            }

            else -> {
                false
            }
        }

    private fun isValidFactoryQualifier(
        qualifier: PsiElement?,
        expression: PsiElement,
    ): Boolean {
        qualifier ?: return true
        if (qualifier is PsiThisExpression) {
            return true
        }
        if (qualifier is PsiReferenceExpression) {
            if (qualifier.referenceName == "this") {
                return true
            }
            val resolved = qualifier.resolve() as? PsiClass ?: return false
            return isSameOrEnclosingOrSuperClass(resolved, expression)
        }
        return false
    }

    private fun isSameOrEnclosingOrSuperClass(
        resolved: PsiClass,
        context: PsiElement,
    ): Boolean {
        var current = PsiTreeUtil.getParentOfType(context, PsiClass::class.java)
        while (current != null) {
            if (current.isEquivalentTo(resolved) || current.isInheritor(resolved, true)) {
                return true
            }
            current = current.containingClass
        }
        return false
    }

    fun referencesServerHttpUri(
        expression: PsiElement,
        serverVariableName: String,
    ): Boolean {
        val call = expression as? PsiMethodCallExpression ?: return false
        val receiver = call.methodExpression.qualifierExpression ?: return false
        return matchesServerReceiver(receiver, serverVariableName) &&
            call.methodExpression.referenceName == "httpUri"
    }

    internal fun resolveScopedExtension(extensions: List<ArmeriaJUnitServerExtension>): ArmeriaJUnitServerExtension? {
        // When multiple ServerExtension fields exist in scope, do not guess which server a call
        // targets — inspection and test generation treat the scope as ambiguous.
        if (extensions.size != 1) {
            return null
        }
        return extensions.single()
    }

    internal fun referencesServerVariable(
        element: PsiElement,
        serverVariableName: String,
    ): Boolean {
        val call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false) ?: return false
        return serverVariableName in referencedServerVariableNamesFromJavaCall(call)
    }

    private fun referencedServerVariableNamesFromJavaCall(call: PsiMethodCallExpression): Set<String> {
        val names = linkedSetOf<String>()
        var qualifier: PsiElement? = call.methodExpression.qualifierExpression
        while (qualifier != null) {
            when (qualifier) {
                is PsiReferenceExpression -> {
                    qualifier.referenceName?.let(names::add)
                    qualifier = null
                }

                is PsiMethodCallExpression -> {
                    (qualifier.methodExpression.qualifierExpression as? PsiReferenceExpression)
                        ?.referenceName
                        ?.let(names::add)
                    qualifier = qualifier.methodExpression.qualifierExpression
                }

                else -> {
                    break
                }
            }
        }
        return names
    }
}
