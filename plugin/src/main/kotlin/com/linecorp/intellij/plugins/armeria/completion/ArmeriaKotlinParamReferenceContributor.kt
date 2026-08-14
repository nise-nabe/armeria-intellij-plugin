package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinAnnotationSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ArmeriaKotlinParamReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            ArmeriaKotlinParamReferenceProvider(),
        )
    }
}

private class ArmeriaKotlinParamReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val template = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        if (!template.isConstantString()) {
            return PsiReference.EMPTY_ARRAY
        }
        val value = template.constantStringValue() ?: return PsiReference.EMPTY_ARRAY
        val entry = PsiTreeUtil.getParentOfType(template, KtAnnotationEntry::class.java) ?: return PsiReference.EMPTY_ARRAY
        val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: return PsiReference.EMPTY_ARRAY
        val function = PsiTreeUtil.getParentOfType(entry, KtNamedFunction::class.java) ?: return PsiReference.EMPTY_ARRAY
        val valueRange = ElementManipulators.getValueTextRange(template)
        if (qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION) {
            return arrayOf(ArmeriaKotlinParamNameReference(template, valueRange, function, value))
        }
        if (!isKotlinPathAnnotation(qualifiedName)) {
            return PsiReference.EMPTY_ARRAY
        }
        return ArmeriaPathVariableSupport
            .pathVariableOccurrences(value)
            .map { occurrence ->
                ArmeriaKotlinPathVariableReference(
                    template = template,
                    rangeInElement =
                        TextRange(
                            valueRange.startOffset + occurrence.startOffset,
                            valueRange.startOffset + occurrence.endOffset,
                        ),
                    function = function,
                    variableName = occurrence.name,
                )
            }.toTypedArray()
    }
}

private fun isKotlinPathAnnotation(qualifiedName: String): Boolean =
    qualifiedName in ArmeriaRouteSupport.routeAnnotations ||
        qualifiedName == ArmeriaRouteSupport.PATH_ANNOTATION ||
        qualifiedName == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION

private class ArmeriaKotlinParamNameReference(
    template: KtStringTemplateExpression,
    rangeInElement: TextRange,
    private val function: KtNamedFunction,
    private val variableName: String,
) : PsiReferenceBase<KtStringTemplateExpression>(template, rangeInElement) {
    override fun resolve(): PsiElement = element

    override fun getVariants(): Array<Any> =
        ArmeriaKotlinAnnotationSupport
            .pathVariables(function)
            .map { name ->
                LookupElementBuilder
                    .create(name)
                    .withTypeText(message("completion.param.path.variable.type"))
            }.toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        renameKotlinPathVariable(function, variableName, newElementName)
        return element
    }
}

private class ArmeriaKotlinPathVariableReference(
    template: KtStringTemplateExpression,
    rangeInElement: TextRange,
    private val function: KtNamedFunction,
    private val variableName: String,
) : PsiReferenceBase<KtStringTemplateExpression>(template, rangeInElement, true) {
    override fun resolve(): PsiElement = element

    override fun handleElementRename(newElementName: String): PsiElement {
        renameKotlinPathVariable(function, variableName, newElementName)
        return element
    }
}

internal fun renameKotlinPathVariable(
    function: KtNamedFunction,
    oldName: String,
    newName: String,
) {
    if (oldName.isEmpty() || oldName == newName) {
        return
    }
    val entries = mutableListOf<KtAnnotationEntry>()
    entries += function.annotationEntries
    PsiTreeUtil.getParentOfType(function, KtClassOrObject::class.java)?.annotationEntries?.let { entries += it }
    function.valueParameters.forEach { parameter -> entries += parameter.annotationEntries }
    for (entry in entries) {
        val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: continue
        when {
            isKotlinPathAnnotation(qualifiedName) ->
                replaceKotlinAnnotationStrings(entry) { ArmeriaPathVariableSupport.replacePathVariableName(it, oldName, newName) }
            qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION ->
                replaceKotlinAnnotationStrings(entry) { current -> if (current == oldName) newName else current }
        }
    }
}

private fun replaceKotlinAnnotationStrings(
    entry: KtAnnotationEntry,
    transform: (String) -> String,
) {
    for (argument in entry.valueArguments) {
        val expression = argument.getArgumentExpression() as? KtStringTemplateExpression ?: continue
        if (!expression.isConstantString()) {
            continue
        }
        val current = expression.constantStringValue() ?: continue
        val updated = transform(current)
        if (updated != current) {
            ElementManipulators.handleContentChange(expression, updated)
        }
    }
}

internal fun KtStringTemplateExpression.isConstantString(): Boolean = entries.size <= 1

internal fun KtStringTemplateExpression.constantStringValue(): String? {
    if (entries.isEmpty()) {
        return ""
    }
    if (entries.size != 1) {
        return null
    }
    return entries[0].text
}

internal fun cookieNamesInKotlinClass(owner: KtClassOrObject): Set<String> {
    val names = linkedSetOf<String>()
    for (declaration in owner.declarations.filterIsInstance<KtNamedFunction>()) {
        for (parameter in declaration.valueParameters) {
            cookieName(parameter)?.let { names += it }
        }
    }
    return names
}

private fun cookieName(parameter: KtParameter): String? {
    val entry =
        parameter.annotationEntries.firstOrNull {
            ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.COOKIE_ANNOTATION
        } ?: return null
    return ArmeriaKotlinAnnotationSupport.extractStrings(entry).firstOrNull { it.isNotBlank() }
}
