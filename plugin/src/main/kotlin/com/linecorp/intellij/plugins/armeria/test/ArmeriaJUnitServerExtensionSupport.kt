package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaJUnitServerExtensionSupport {
    const val REGISTER_EXTENSION_ANNOTATION = "org.junit.jupiter.api.extension.RegisterExtension"
    const val REGISTER_EXTENSION_ANNOTATION_SHORT = "RegisterExtension"
    const val SERVER_EXTENSION_CLASS = "com.linecorp.armeria.testing.junit5.server.ServerExtension"
    const val WEB_CLIENT_CLASS = "com.linecorp.armeria.client.WebClient"
    const val BLOCKING_WEB_CLIENT_CLASS = "com.linecorp.armeria.client.blocking.BlockingWebClient"

    fun hasRegisterExtensionAnnotation(owner: PsiModifierListOwner): Boolean =
        owner.annotations.any {
            it.qualifiedName == REGISTER_EXTENSION_ANNOTATION
        }

    fun isServerExtensionType(
        type: PsiType?,
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean {
        val resolvedClass = (type as? PsiClassType)?.resolve() ?: return false
        return isServerExtensionClass(resolvedClass, project, scope)
    }

    fun isServerExtensionClass(
        psiClass: PsiClass,
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        if (qualifiedName == SERVER_EXTENSION_CLASS) {
            return true
        }
        val baseClass = JavaPsiFacade.getInstance(project).findClass(SERVER_EXTENSION_CLASS, scope) ?: return false
        return psiClass.isInheritor(baseClass, true)
    }

    fun serverExtensionFromField(
        field: PsiField,
        scope: GlobalSearchScope,
    ): ArmeriaJUnitServerExtension? {
        if (!hasRegisterExtensionAnnotation(field) || !isServerExtensionType(field.type, field.project, scope)) {
            return null
        }
        val variableName = field.name ?: return null
        val containingClass = field.containingClass ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = field,
            variableName = variableName,
            containingClassName = containingClass.qualifiedName.orEmpty(),
            moduleName = ArmeriaTestMetadata.moduleName(field),
        )
    }

    fun serverExtensionsInClass(
        psiClass: PsiClass,
        scope: GlobalSearchScope,
    ): List<ArmeriaJUnitServerExtension> {
        if (toKtClass(psiClass) != null) {
            return emptyList()
        }
        return psiClass.fields.mapNotNull {
            serverExtensionFromField(it, scope)
        }
    }

    fun serverExtensionFromKotlinProperty(
        property: KtProperty,
        scope: GlobalSearchScope,
    ): ArmeriaJUnitServerExtension? {
        if (!property.annotationEntries.any { it.shortName?.asString() == "RegisterExtension" }) {
            return null
        }
        if (!isKotlinServerExtensionProperty(property, property.project, scope)) {
            return null
        }
        val variableName = property.name ?: return null
        val containingClass = property.getParentOfType<KtClass>(true) ?: return null
        return ArmeriaJUnitServerExtension.create(
            element = property,
            variableName = variableName,
            containingClassName = containingClass.fqName?.asString().orEmpty(),
            moduleName = ArmeriaTestMetadata.moduleName(property),
        )
    }

    fun fileMayContainRegisterExtension(fileText: String): Boolean =
        REGISTER_EXTENSION_ANNOTATION in fileText || "@$REGISTER_EXTENSION_ANNOTATION_SHORT" in fileText

    fun enclosingServerExtension(
        element: PsiElement,
        scope: GlobalSearchScope,
    ): ArmeriaJUnitServerExtension? {
        val project = element.project
        val className =
            element.getParentOfType<KtClass>(true)?.fqName?.asString()
                ?: PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName
                ?: return null
        val extensions =
            ArmeriaJUnitServerExtensionCollector
                .collect(project)
                .filter { extension -> isEnclosingOrNestedTestClass(className, extension.containingClassName) }
        if (extensions.isEmpty()) {
            return null
        }
        return resolveScopedExtension(element, extensions)
    }

    private fun isEnclosingOrNestedTestClass(
        testClassName: String,
        extensionClassName: String,
    ): Boolean =
        testClassName == extensionClassName ||
            testClassName.startsWith("$extensionClassName.") ||
            testClassName.startsWith("$extensionClassName$")

    fun toKtClass(psiClass: PsiClass): KtClass? {
        (psiClass as? KtClass)?.let { return it }
        (psiClass.navigationElement as? KtClass)?.let { return it }
        (psiClass.originalElement as? KtClass)?.let { return it }
        val qualifiedName = psiClass.qualifiedName ?: return null
        val ktFile = psiClass.containingFile as? KtFile ?: return null
        return ktFile.declarations.filterIsInstance<KtClass>().firstOrNull { it.fqName?.asString() == qualifiedName }
    }

    private fun isKotlinServerExtensionProperty(
        property: KtProperty,
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean {
        property.typeReference?.let { typeReference ->
            when (val resolved = typeReference.references.firstOrNull()?.resolve()) {
                is PsiClass -> return isServerExtensionClass(resolved, project, scope)
                is KtClass ->
                    return resolved.toLightClass()?.let { isServerExtensionClass(it, project, scope) } == true
            }
            if (typeReference.text.contains("ServerExtension")) {
                return true
            }
        }
        val initializer = property.initializer ?: return false
        return isKotlinServerExtensionInitializer(initializer, project, scope)
    }

    private fun isKotlinServerExtensionInitializer(
        initializer: KtExpression,
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean {
        val objectDeclaration =
            when (initializer) {
                is KtObjectLiteralExpression -> initializer.objectDeclaration
                else -> null
            } ?: return false
        return objectDeclaration.superTypeListEntries.any { entry ->
            when (val resolved = entry.typeReference?.references?.firstOrNull()?.resolve()) {
                is PsiClass -> isServerExtensionClass(resolved, project, scope)
                is KtClass -> resolved.toLightClass()?.let { isServerExtensionClass(it, project, scope) } == true
                else -> entry.typeReference?.text?.contains("ServerExtension") == true
            }
        }
    }

    fun referencesServerHttpUri(
        expression: PsiElement,
        serverVariableName: String,
    ): Boolean {
        (expression as? PsiMethodCallExpression)?.let { call ->
            val receiver = call.methodExpression.qualifierExpression as? PsiReferenceExpression
            if (receiver?.referenceName == serverVariableName && call.methodExpression.referenceName == "httpUri") {
                return true
            }
        }
        (expression as? KtCallExpression)?.let { call ->
            if (call.calleeExpression?.text != "httpUri") {
                return false
            }
            val parent = call.parent as? KtDotQualifiedExpression ?: return false
            if (parent.selectorExpression != call) {
                return false
            }
            val receiver = parent.receiverExpression as? KtNameReferenceExpression ?: return false
            return receiver.getReferencedName() == serverVariableName
        }
        (expression as? KtDotQualifiedExpression)?.let { qualified ->
            val call = qualified.selectorExpression as? KtCallExpression ?: return false
            if (call.calleeExpression?.text != "httpUri") {
                return false
            }
            val receiver = qualified.receiverExpression as? KtNameReferenceExpression ?: return false
            return receiver.getReferencedName() == serverVariableName
        }
        return false
    }

    private fun resolveScopedExtension(
        element: PsiElement,
        extensions: List<ArmeriaJUnitServerExtension>,
    ): ArmeriaJUnitServerExtension? {
        if (extensions.size == 1) {
            return extensions.single()
        }
        val matches = extensions.filter { it.variableName in referencedServerVariableNames(element) }
        return if (matches.size == 1) matches.single() else null
    }

    internal fun referencesServerVariable(
        element: PsiElement,
        serverVariableName: String,
    ): Boolean = serverVariableName in referencedServerVariableNames(element)

    private fun referencedServerVariableNames(element: PsiElement): Set<String> {
        PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false)?.let { call ->
            return referencedServerVariableNamesFromKotlinCall(call)
        }
        PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false)?.let { call ->
            return referencedServerVariableNamesFromJavaCall(call)
        }
        return emptySet()
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
                else -> break
            }
        }
        return names
    }

    private fun referencedServerVariableNamesFromKotlinCall(call: KtCallExpression): Set<String> {
        val names = linkedSetOf<String>()
        var current: KtExpression = call
        while (true) {
            val parent = current.parent as? KtDotQualifiedExpression ?: break
            if (parent.selectorExpression != current) {
                break
            }
            collectKotlinReceiverChainNames(parent.receiverExpression, names)
            current = parent.receiverExpression
        }
        return names
    }

    private fun collectKotlinReceiverChainNames(
        expression: KtExpression,
        names: MutableSet<String>,
    ) {
        when (expression) {
            is KtNameReferenceExpression -> expression.getReferencedName()?.let(names::add)
            is KtCallExpression -> {
                (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()?.let(names::add)
                (expression.parent as? KtDotQualifiedExpression)
                    ?.takeIf { it.selectorExpression == expression }
                    ?.receiverExpression
                    ?.let { collectKotlinReceiverChainNames(it, names) }
            }
            is KtDotQualifiedExpression -> {
                expression.selectorExpression?.let { collectKotlinReceiverChainNames(it, names) }
                collectKotlinReceiverChainNames(expression.receiverExpression, names)
            }
        }
    }
}
