package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinAnnotationSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ArmeriaKotlinAnnotationValueCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(KtAnnotationEntry::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val start = parameters.originalPosition ?: parameters.position
                    val template =
                        PsiTreeUtil.getParentOfType(start, KtStringTemplateExpression::class.java, false)
                            ?: PsiTreeUtil.getParentOfType(
                                parameters.position,
                                KtStringTemplateExpression::class.java,
                                false,
                            )
                            ?: return
                    val entry =
                        PsiTreeUtil.getParentOfType(template, KtAnnotationEntry::class.java)
                            ?: PsiTreeUtil.getParentOfType(start, KtAnnotationEntry::class.java)
                            ?: return
                    val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry) ?: return
                    when (qualifiedName) {
                        ArmeriaRouteSupport.HEADER_ANNOTATION -> addHeaderCompletions(result)
                        ArmeriaRouteSupport.COOKIE_ANNOTATION -> {
                            val owner = PsiTreeUtil.getParentOfType(entry, KtClassOrObject::class.java) ?: return
                            for (name in cookieNamesInKotlinClass(owner)) {
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
                    }
                }
            },
        )
    }
}
