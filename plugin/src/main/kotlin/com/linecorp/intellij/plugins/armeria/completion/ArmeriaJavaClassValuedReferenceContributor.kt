package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.JavaPsiFacade
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
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class ArmeriaJavaClassValuedReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiIdentifier::class.java),
            ArmeriaJavaClassValuedIdentifierReferenceProvider(),
        )
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiJavaCodeReferenceElement::class.java),
            ArmeriaJavaClassValuedCodeReferenceProvider(),
        )
    }
}

private class ArmeriaJavaClassValuedIdentifierReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val identifier = element as? PsiIdentifier ?: return PsiReference.EMPTY_ARRAY
        val classRef = identifier.parent as? PsiJavaCodeReferenceElement ?: return PsiReference.EMPTY_ARRAY
        if (classRef.referenceNameElement != identifier || !isClassNameOfClassLiteral(classRef)) {
            return PsiReference.EMPTY_ARRAY
        }
        return arrayOf(ArmeriaJavaAnnotationClassReference(identifier, classRef))
    }
}

private class ArmeriaJavaClassValuedCodeReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val classRef = element as? PsiJavaCodeReferenceElement ?: return PsiReference.EMPTY_ARRAY
        if (!isClassNameOfClassLiteral(classRef)) {
            return PsiReference.EMPTY_ARRAY
        }
        return arrayOf(ArmeriaJavaAnnotationClassCodeReference(classRef))
    }
}

private fun isClassNameOfClassLiteral(classRef: PsiJavaCodeReferenceElement): Boolean {
    if (classRef.parent is PsiJavaCodeReferenceElement) {
        return false
    }
    if (PsiTreeUtil.getParentOfType(classRef, PsiClassObjectAccessExpression::class.java) == null) {
        return false
    }
    return javaClassValuedAnnotation(classRef) != null
}

private class ArmeriaJavaAnnotationClassReference(
    identifier: PsiIdentifier,
    private val classRef: PsiJavaCodeReferenceElement,
) : PsiReferenceBase<PsiIdentifier>(identifier, TextRange(0, identifier.textLength), true) {
    override fun resolve(): PsiElement? = resolveClassLiteral(element, classRef)

    override fun getVariants(): Array<Any> = classValuedLookup(classRef, element)
}

private class ArmeriaJavaAnnotationClassCodeReference(
    classRef: PsiJavaCodeReferenceElement,
) : PsiReferenceBase<PsiJavaCodeReferenceElement>(classRef, true) {
    override fun resolve(): PsiElement? = resolveClassLiteral(element, element)

    override fun getVariants(): Array<Any> = classValuedLookup(element, element)
}

private fun resolveClassLiteral(
    context: PsiElement,
    classRef: PsiJavaCodeReferenceElement,
): PsiClass? {
    val qualifiedName = classRef.qualifiedName
    if (!qualifiedName.isNullOrBlank() && qualifiedName.contains('.')) {
        resolveQualifiedClass(context, qualifiedName)?.let { return it }
        return classRef.resolve() as? PsiClass
    }
    (classRef.resolve() as? PsiClass)?.let { return it }
    return resolveClassByName(context, classRef.referenceName ?: classRef.text)
}

internal fun resolveQualifiedClass(
    context: PsiElement,
    qualifiedName: String,
): PsiClass? =
    try {
        JavaPsiFacade.getInstance(context.project).findClass(
            qualifiedName,
            GlobalSearchScope.projectScope(context.project),
        )
    } catch (exception: ProcessCanceledException) {
        throw exception
    } catch (_: IndexNotReadyException) {
        null
    }

private fun classValuedLookup(
    classRef: PsiJavaCodeReferenceElement,
    context: PsiElement,
): Array<Any> {
    val annotation = javaClassValuedAnnotation(classRef) ?: return emptyArray()
    return ArmeriaClassValuedAnnotationSupport
        .lookupElements(context, annotation.qualifiedName, kotlinClassLiteral = false)
        .toTypedArray()
}

internal fun resolveClassByName(
    context: PsiElement,
    name: String,
): PsiClass? {
    if (name.isBlank()) {
        return null
    }
    val file = context.containingFile as? PsiClassOwner ?: return null
    file.classes.firstOrNull { it.name == name }?.let { return it }
    for (owner in file.classes) {
        owner.innerClasses.firstOrNull { it.name == name }?.let { return it }
    }
    return try {
        val matches =
            PsiShortNamesCache.getInstance(context.project).getClassesByName(
                name,
                GlobalSearchScope.projectScope(context.project),
            )
        when {
            matches.size == 1 -> matches[0]
            else -> {
                val filePackage = file.packageName
                matches.firstOrNull { candidate ->
                    candidate.qualifiedName?.substringBeforeLast('.', missingDelimiterValue = "") ==
                        filePackage
                }
            }
        }
    } catch (exception: ProcessCanceledException) {
        throw exception
    } catch (_: IndexNotReadyException) {
        null
    }
}
