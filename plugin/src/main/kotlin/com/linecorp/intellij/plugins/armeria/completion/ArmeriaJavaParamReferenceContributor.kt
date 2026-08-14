package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaParamPathVariableMismatch
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaJavaParamReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiLiteralExpression::class.java),
            ArmeriaJavaParamReferenceProvider(),
        )
    }
}

private class ArmeriaJavaParamReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        val literal = element as? PsiLiteralExpression ?: return PsiReference.EMPTY_ARRAY
        val value = literal.value as? String ?: return PsiReference.EMPTY_ARRAY
        val annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java) ?: return PsiReference.EMPTY_ARRAY
        val qualifiedName = annotation.qualifiedName ?: return PsiReference.EMPTY_ARRAY
        val method = PsiTreeUtil.getParentOfType(annotation, PsiMethod::class.java) ?: return PsiReference.EMPTY_ARRAY
        val valueRange = ElementManipulators.getValueTextRange(literal)
        if (qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION) {
            return arrayOf(ArmeriaJavaParamNameReference(literal, valueRange, method, value))
        }
        if (!isPathAnnotation(qualifiedName)) {
            return PsiReference.EMPTY_ARRAY
        }
        return ArmeriaPathVariableSupport
            .pathVariableOccurrences(value)
            .map { occurrence ->
                ArmeriaJavaPathVariableReference(
                    literal = literal,
                    rangeInElement =
                        TextRange(
                            valueRange.startOffset + occurrence.startOffset,
                            valueRange.startOffset + occurrence.endOffset,
                        ),
                    method = method,
                    variableName = occurrence.name,
                )
            }.toTypedArray()
    }
}

private fun isPathAnnotation(qualifiedName: String): Boolean =
    qualifiedName in ArmeriaRouteSupport.routeAnnotations ||
        qualifiedName == ArmeriaRouteSupport.PATH_ANNOTATION ||
        qualifiedName == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION

private class ArmeriaJavaParamNameReference(
    literal: PsiLiteralExpression,
    rangeInElement: TextRange,
    private val method: PsiMethod,
    private val variableName: String,
) : PsiReferenceBase<PsiLiteralExpression>(literal, rangeInElement) {
    override fun resolve(): PsiElement = element

    override fun getVariants(): Array<Any> =
        ArmeriaParamPathVariableMismatch
            .pathVariables(method)
            .map { name ->
                LookupElementBuilder
                    .create(name)
                    .withTypeText(message("completion.param.path.variable.type"))
            }.toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        renameJavaPathVariable(method, variableName, newElementName)
        return element
    }
}

private class ArmeriaJavaPathVariableReference(
    literal: PsiLiteralExpression,
    rangeInElement: TextRange,
    private val method: PsiMethod,
    private val variableName: String,
) : PsiReferenceBase<PsiLiteralExpression>(literal, rangeInElement, true) {
    override fun resolve(): PsiElement = element

    override fun handleElementRename(newElementName: String): PsiElement {
        renameJavaPathVariable(method, variableName, newElementName)
        return element
    }
}

internal fun renameJavaPathVariable(
    method: PsiMethod,
    oldName: String,
    newName: String,
) {
    if (oldName.isEmpty() || oldName == newName) {
        return
    }
    val annotations = mutableListOf<PsiAnnotation>()
    annotations += method.annotations
    method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION)?.let { annotations += it }
    method.parameterList.parameters.forEach { parameter ->
        parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION)?.let { annotations += it }
    }
    for (annotation in annotations) {
        val qualifiedName = annotation.qualifiedName ?: continue
        when {
            isPathAnnotation(qualifiedName) ->
                replaceJavaAnnotationStrings(annotation) { ArmeriaPathVariableSupport.replacePathVariableName(it, oldName, newName) }
            qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION ->
                replaceJavaAnnotationStrings(annotation) { current -> if (current == oldName) newName else current }
        }
    }
}

private fun replaceJavaAnnotationStrings(
    annotation: PsiAnnotation,
    transform: (String) -> String,
) {
    listOf("value", "path").forEach { attribute ->
        replaceMemberValue(annotation.findDeclaredAttributeValue(attribute), transform)
    }
}

private fun replaceMemberValue(
    value: PsiAnnotationMemberValue?,
    transform: (String) -> String,
) {
    when (value) {
        is PsiLiteralExpression -> {
            val current = value.value as? String ?: return
            val updated = transform(current)
            if (updated != current) {
                ElementManipulators.handleContentChange(value, updated)
            }
        }
        is PsiArrayInitializerMemberValue -> value.initializers.forEach { replaceMemberValue(it, transform) }
    }
}
