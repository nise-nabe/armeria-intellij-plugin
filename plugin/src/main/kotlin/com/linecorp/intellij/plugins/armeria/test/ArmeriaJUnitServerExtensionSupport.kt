package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtClass
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
    ): List<ArmeriaJUnitServerExtension> =
        psiClass.fields.mapNotNull {
            serverExtensionFromField(it, scope)
        }

    fun serverExtensionFromKotlinProperty(
        property: KtProperty,
        scope: GlobalSearchScope,
    ): ArmeriaJUnitServerExtension? {
        if (!property.annotationEntries.any { it.shortName?.asString() == "RegisterExtension" }) {
            return null
        }
        val typeText = property.typeReference?.text ?: return null
        if (!typeText.contains("ServerExtension")) {
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
        var current: PsiElement? = element
        while (current != null) {
            if (current is PsiClass) {
                serverExtensionsInClass(current, scope).firstOrNull()?.let { return it }
            }
            current = current.parent
        }
        return null
    }
}
