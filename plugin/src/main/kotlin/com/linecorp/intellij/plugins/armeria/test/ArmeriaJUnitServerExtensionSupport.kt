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
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object ArmeriaJUnitServerExtensionSupport {
    const val REGISTER_EXTENSION_ANNOTATION = "org.junit.jupiter.api.extension.RegisterExtension"
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

    fun enclosingServerExtension(
        element: PsiElement,
        scope: GlobalSearchScope,
    ): ArmeriaJUnitServerExtension? {
        val project = element.project
        element.getParentOfType<KtClass>(true)?.fqName?.asString()?.let { className ->
            val extensions =
                ArmeriaJUnitServerExtensionCollector
                    .collect(project)
                    .filter { it.containingClassName == className }
            if (extensions.isNotEmpty()) {
                return resolveScopedExtension(element, extensions)
            }
        }
        PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.let { psiClass ->
            val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, psiClass)
            if (extensions.isNotEmpty()) {
                return resolveScopedExtension(element, extensions)
            }
        }
        return null
    }

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
        val typeReference = property.typeReference ?: return false
        when (val resolved = typeReference.references.firstOrNull()?.resolve()) {
            is PsiClass -> return isServerExtensionClass(resolved, project, scope)
            is KtClass ->
                return resolved.toLightClass()?.let { isServerExtensionClass(it, project, scope) } == true
        }
        return typeReference.text.contains("ServerExtension")
    }

    private fun resolveScopedExtension(
        element: PsiElement,
        extensions: List<ArmeriaJUnitServerExtension>,
    ): ArmeriaJUnitServerExtension? {
        if (extensions.size == 1) {
            return extensions.single()
        }
        val referencedNames = referencedServerVariableNames(element)
        extensions
            .filter { it.variableName in referencedNames }
            .let { matches ->
                if (matches.size == 1) {
                    return matches.single()
                }
                if (matches.isNotEmpty()) {
                    return matches.first()
                }
            }
        return extensions.firstOrNull()
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
