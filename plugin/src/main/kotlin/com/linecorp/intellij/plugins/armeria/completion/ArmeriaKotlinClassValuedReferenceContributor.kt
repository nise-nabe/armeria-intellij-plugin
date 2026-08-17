package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinAnnotationSupport
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class ArmeriaKotlinClassValuedReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtNameReferenceExpression::class.java),
            ArmeriaKotlinClassValuedReferenceProvider(),
        )
    }
}

private class ArmeriaKotlinClassValuedReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val reference = element as? KtNameReferenceExpression ?: return PsiReference.EMPTY_ARRAY
        if (PsiTreeUtil.getParentOfType(reference, KtClassLiteralExpression::class.java) == null) {
            return PsiReference.EMPTY_ARRAY
        }
        if (kotlinClassValuedEntry(reference) == null) {
            return PsiReference.EMPTY_ARRAY
        }
        return arrayOf(ArmeriaKotlinAnnotationClassReference(reference))
    }
}

private class ArmeriaKotlinAnnotationClassReference(
    reference: KtNameReferenceExpression,
) : PsiReferenceBase<KtNameReferenceExpression>(reference, TextRange(0, reference.textLength)) {
    override fun resolve(): PsiElement? {
        val name = element.getReferencedName()
        resolveClassByName(element, name)?.let { return it }
        return findClassOrObject(element.containingKtFile, name)
    }

    override fun getVariants(): Array<Any> {
        val entry = kotlinClassValuedEntry(element) ?: return emptyArray()
        return ArmeriaClassValuedAnnotationSupport
            .lookupElements(
                element,
                ArmeriaKotlinAnnotationSupport.qualifiedName(entry),
                kotlinClassLiteral = true,
            ).toTypedArray()
    }
}

private fun findClassOrObject(
    file: KtFile,
    name: String,
): KtClassOrObject? {
    val pending = ArrayDeque<KtClassOrObject>()
    pending.addAll(file.declarations.filterIsInstance<KtClassOrObject>())
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (current.name == name) {
            return current
        }
        pending.addAll(current.declarations.filterIsInstance<KtClassOrObject>())
    }
    return null
}
