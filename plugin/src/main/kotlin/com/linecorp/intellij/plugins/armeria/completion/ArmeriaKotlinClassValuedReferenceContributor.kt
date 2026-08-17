package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiClass
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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
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
        if (!isClassNameOfClassLiteral(reference)) {
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
) : PsiReferenceBase<KtNameReferenceExpression>(reference, TextRange(0, reference.textLength), true) {
    override fun resolve(): PsiElement? {
        qualifiedClassName()?.let { fqn ->
            resolveQualifiedClass(element, fqn)?.let { return it }
            return nativeClassOrObject()
        }
        nativeClassOrObject()?.let { return it }
        val name = element.getReferencedName()
        findClassOrObject(element.containingKtFile, name)?.let { return it }
        return resolveClassByName(element, name)
    }

    private fun nativeClassOrObject(): PsiElement? =
        resolveViaOtherReferences()?.takeIf { resolved ->
            resolved is PsiClass || resolved is KtClassOrObject
        }

    private fun qualifiedClassName(): String? {
        val qualified = element.parent as? KtDotQualifiedExpression ?: return null
        if (qualified.selectorExpression != element) {
            return null
        }
        val text = qualified.text
        return text.takeIf { '.' in it && text.none(Char::isWhitespace) }
    }

    private fun resolveViaOtherReferences(): PsiElement? =
        try {
            element.references.firstNotNullOfOrNull { reference ->
                if (reference is ArmeriaKotlinAnnotationClassReference) {
                    null
                } else {
                    reference.resolve()
                }
            }
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            null
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

private fun isClassNameOfClassLiteral(reference: KtNameReferenceExpression): Boolean {
    val literal = PsiTreeUtil.getParentOfType(reference, KtClassLiteralExpression::class.java) ?: return false
    return when (val receiver = literal.receiverExpression) {
        is KtNameReferenceExpression -> receiver == reference
        is KtDotQualifiedExpression -> receiver.selectorExpression == reference
        else -> false
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
