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
            PlatformPatterns.psiElement().inside(PsiLiteralExpression::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val literal =
                        PsiTreeUtil.getParentOfType(parameters.position, PsiLiteralExpression::class.java)
                            ?: return
                    val annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java) ?: return
                    val qualifiedName = annotation.qualifiedName ?: return
                    when (qualifiedName) {
                        ArmeriaRouteSupport.HEADER_ANNOTATION -> addHeaderCompletions(result)
                        ArmeriaRouteSupport.COOKIE_ANNOTATION -> {
                            val owner = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java) ?: return
                            addCookieCompletions(owner, result)
                        }
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
    val names =
        owner.methods
            .asSequence()
            .flatMap { method -> method.parameterList.parameters.asSequence() }
            .mapNotNull { parameter ->
                val annotation = parameter.getAnnotation(ArmeriaRouteSupport.COOKIE_ANNOTATION) ?: return@mapNotNull null
                ArmeriaRouteSupport.extractStrings(annotation.findDeclaredAttributeValue("value")).firstOrNull { it.isNotBlank() }
            }.toSet()
    for (name in names) {
        if (!result.prefixMatcher.prefixMatches(name)) {
            continue
        }
        result.addElement(
            LookupElementBuilder
                .create(name)
                .withTypeText(message("completion.cookie.type")),
        )
    }
}
