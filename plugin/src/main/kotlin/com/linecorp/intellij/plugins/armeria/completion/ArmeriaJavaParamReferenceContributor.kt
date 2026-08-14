package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
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
        val method = PsiTreeUtil.getParentOfType(annotation, PsiMethod::class.java)
        val ownerClass = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java)
        val valueRange = ElementManipulators.getValueTextRange(literal)
        if (qualifiedName == ArmeriaRouteSupport.PARAM_ANNOTATION) {
            if (method == null) {
                return PsiReference.EMPTY_ARRAY
            }
            return arrayOf(ArmeriaJavaParamNameReference(literal, valueRange, method, value))
        }
        if (!isPathAnnotation(qualifiedName)) {
            return PsiReference.EMPTY_ARRAY
        }
        val occurrences = ArmeriaPathVariableSupport.pathVariableOccurrences(value)
        if (method != null) {
            return occurrences
                .map { occurrence ->
                    ArmeriaJavaPathVariableReference(
                        literal = literal,
                        rangeInElement = pathVariableRangeInHost(literal, occurrence),
                        method = method,
                        variableName = occurrence.name,
                    )
                }.toTypedArray()
        }
        if (qualifiedName == ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION && ownerClass != null) {
            return occurrences
                .map { occurrence ->
                    ArmeriaJavaPathPrefixVariableReference(
                        literal = literal,
                        rangeInElement = pathVariableRangeInHost(literal, occurrence),
                        ownerClass = ownerClass,
                        variableName = occurrence.name,
                    )
                }.toTypedArray()
        }
        return PsiReference.EMPTY_ARRAY
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

private class ArmeriaJavaPathPrefixVariableReference(
    literal: PsiLiteralExpression,
    rangeInElement: TextRange,
    private val ownerClass: PsiClass,
    private val variableName: String,
) : PsiReferenceBase<PsiLiteralExpression>(literal, rangeInElement, true) {
    override fun resolve(): PsiElement = element

    override fun handleElementRename(newElementName: String): PsiElement {
        renameJavaClassPathVariable(ownerClass, variableName, newElementName)
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
    val owner = method.containingClass
    if (owner != null && classPrefixHasVariable(owner, oldName)) {
        renameJavaClassPathVariable(owner, oldName, newName)
        return
    }
    rewriteJavaPathVariableAnnotations(javaAnnotationsOnMethod(method), oldName, newName)
    renameJavaImplicitParams(method, oldName, newName)
}

internal fun renameJavaClassPathVariable(
    owner: PsiClass,
    oldName: String,
    newName: String,
) {
    if (oldName.isEmpty() || oldName == newName) {
        return
    }
    val annotations = mutableListOf<PsiAnnotation>()
    owner.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION)?.let { annotations += it }
    owner.methods
        .filter { it.containingClass == owner }
        .forEach { method ->
            annotations += javaAnnotationsOnMethod(method)
            renameJavaImplicitParams(method, oldName, newName)
        }
    rewriteJavaPathVariableAnnotations(annotations, oldName, newName)
}

private fun classPrefixHasVariable(
    owner: PsiClass,
    name: String,
): Boolean {
    val prefix =
        ArmeriaRouteSupport.extractPrimaryPath(
            owner.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
        )
    return name in ArmeriaPathVariableSupport.extractPathVariables(prefix)
}

private fun javaAnnotationsOnMethod(method: PsiMethod): List<PsiAnnotation> =
    buildList {
        addAll(method.annotations)
        method.parameterList.parameters.forEach { parameter ->
            parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION)?.let { add(it) }
        }
    }

private fun renameJavaImplicitParams(
    method: PsiMethod,
    oldName: String,
    newName: String,
) {
    method.parameterList.parameters.forEach { parameter ->
        val annotation = parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION) ?: return@forEach
        val explicit =
            ArmeriaRouteSupport
                .extractStrings(annotation.findDeclaredAttributeValue("value"))
                .firstOrNull { it.isNotBlank() }
        if (explicit != null || parameter.name != oldName) {
            return@forEach
        }
        parameter.setName(newName)
    }
}

private fun rewriteJavaPathVariableAnnotations(
    annotations: List<PsiAnnotation>,
    oldName: String,
    newName: String,
) {
    for (annotation in annotations) {
        val qualifiedName = annotation.qualifiedName ?: continue
        when {
            isPathAnnotation(qualifiedName) ->
                replaceJavaAnnotationStrings(annotation) {
                    ArmeriaPathVariableSupport.replacePathVariableName(it, oldName, newName)
                }
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
