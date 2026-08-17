package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class ArmeriaJavaClassValuedReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiIdentifier::class.java),
            ArmeriaJavaClassValuedReferenceProvider(),
        )
    }
}

private class ArmeriaJavaClassValuedReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val identifier = element as? PsiIdentifier ?: return PsiReference.EMPTY_ARRAY
        val classRef = identifier.parent as? PsiJavaCodeReferenceElement ?: return PsiReference.EMPTY_ARRAY
        if (PsiTreeUtil.getParentOfType(classRef, PsiClassObjectAccessExpression::class.java) == null) {
            return PsiReference.EMPTY_ARRAY
        }
        if (javaClassValuedAnnotation(classRef) == null) {
            return PsiReference.EMPTY_ARRAY
        }
        return arrayOf(ArmeriaJavaAnnotationClassReference(identifier, classRef))
    }
}

private class ArmeriaJavaAnnotationClassReference(
    identifier: PsiIdentifier,
    private val classRef: PsiJavaCodeReferenceElement,
) : PsiReferenceBase<PsiIdentifier>(identifier, TextRange(0, identifier.textLength)) {
    override fun resolve(): PsiElement? {
        val name = classRef.referenceName ?: element.text
        resolveClassByName(element, name)?.let { return it }
        return classRef.resolve() as? PsiClass
    }

    override fun getVariants(): Array<Any> {
        val annotation = javaClassValuedAnnotation(classRef) ?: return emptyArray()
        return ArmeriaClassValuedAnnotationSupport
            .lookupElements(element, annotation.qualifiedName, kotlinClassLiteral = false)
            .toTypedArray()
    }
}

internal fun resolveClassByName(
    context: PsiElement,
    name: String,
): PsiClass? {
    if (name.isBlank()) {
        return null
    }
    val file = context.containingFile as? PsiClassOwner ?: return null
    return file.classes.firstOrNull { it.name == name }
}
