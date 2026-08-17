package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaAnnotationValueCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiAnnotation::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val start = parameters.originalPosition ?: parameters.position
                    val literal =
                        PsiTreeUtil.getParentOfType(start, PsiLiteralExpression::class.java, false)
                            ?: PsiTreeUtil.getParentOfType(parameters.position, PsiLiteralExpression::class.java, false)
                            ?: return
                    if (literal.value != null && literal.value !is String) {
                        return
                    }
                    val annotation =
                        PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java)
                            ?: PsiTreeUtil.getParentOfType(start, PsiAnnotation::class.java)
                            ?: return
                    val qualifiedName = annotation.qualifiedName ?: return
                    when (qualifiedName) {
                        ArmeriaRouteSupport.HEADER_ANNOTATION -> addHeaderCompletions(result)
                        ArmeriaRouteSupport.COOKIE_ANNOTATION -> {
                            val owner = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java) ?: return
                            addCookieCompletions(owner, result)
                        }
                        ArmeriaRouteSupport.ATTRIBUTE_ANNOTATION -> {
                            val owner = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java) ?: return
                            addNamedValueCompletions(
                                names = attributeNamesInJavaClass(owner),
                                typeTextKey = "completion.attribute.type",
                                result = result,
                            )
                        }
                        ArmeriaRouteSupport.PRODUCES_ANNOTATION,
                        ArmeriaRouteSupport.CONSUMES_ANNOTATION,
                        -> addMediaTypeCompletions(result)
                    }
                }
            },
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PsiAnnotation::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val start = parameters.originalPosition ?: parameters.position
                    val annotation = javaClassValuedAnnotation(start) ?: return
                    for (element in ArmeriaClassValuedAnnotationSupport.lookupElements(
                        start,
                        annotation.qualifiedName,
                        kotlinClassLiteral = false,
                    )) {
                        if (!result.prefixMatcher.prefixMatches(element.lookupString)) {
                            continue
                        }
                        result.addElement(element)
                    }
                }
            },
        )
    }
}

internal fun addHeaderCompletions(result: CompletionResultSet) {
    for (name in ArmeriaKnownHttpHeaders.NAMES) {
        if (!result.prefixMatcher.prefixMatches(name)) {
            continue
        }
        result.addElement(
            LookupElementBuilder
                .create(name)
                .withTypeText(message("completion.header.type")),
        )
    }
}

internal fun addCookieCompletions(
    owner: PsiClass,
    result: CompletionResultSet,
) {
    addNamedValueCompletions(cookieNamesInJavaClass(owner), "completion.cookie.type", result)
}

internal fun addMediaTypeCompletions(result: CompletionResultSet) {
    addNamedValueCompletions(ArmeriaKnownMediaTypes.NAMES, "completion.media.type", result)
}

internal fun addNamedValueCompletions(
    names: Collection<String>,
    typeTextKey: String,
    result: CompletionResultSet,
) {
    val typeText = message(typeTextKey)
    for (name in names) {
        if (!result.prefixMatcher.prefixMatches(name)) {
            continue
        }
        result.addElement(
            LookupElementBuilder
                .create(name)
                .withTypeText(typeText),
        )
    }
}

internal fun cookieNamesInJavaClass(owner: PsiClass): Set<String> = namedParameterValues(owner, ArmeriaRouteSupport.COOKIE_ANNOTATION)

internal fun attributeNamesInJavaClass(owner: PsiClass): Set<String> = namedParameterValues(owner, ArmeriaRouteSupport.ATTRIBUTE_ANNOTATION)

private fun namedParameterValues(
    owner: PsiClass,
    annotationFqn: String,
): Set<String> =
    owner.methods
        .asSequence()
        .flatMap { method -> method.parameterList.parameters.asSequence() }
        .mapNotNull { parameter ->
            val annotation = parameter.getAnnotation(annotationFqn) ?: return@mapNotNull null
            ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value")).firstOrNull { it.isNotBlank() }
        }.toSet()
