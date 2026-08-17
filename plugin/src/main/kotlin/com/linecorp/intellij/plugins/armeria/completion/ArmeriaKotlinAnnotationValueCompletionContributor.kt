package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinAnnotationSupport
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
                            addNamedValueCompletions(
                                names = cookieNamesInKotlinClass(owner),
                                typeTextKey = "completion.cookie.type",
                                result = result,
                            )
                        }
                        ArmeriaRouteSupport.ATTRIBUTE_ANNOTATION -> {
                            val owner = PsiTreeUtil.getParentOfType(entry, KtClassOrObject::class.java) ?: return
                            addNamedValueCompletions(
                                names = attributeNamesInKotlinClass(owner),
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
            PlatformPatterns.psiElement().inside(KtAnnotationEntry::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val start = parameters.originalPosition ?: parameters.position
                    val entry = kotlinClassValuedEntry(start) ?: return
                    for (element in ArmeriaClassValuedAnnotationSupport.lookupElements(
                        start,
                        ArmeriaKotlinAnnotationSupport.qualifiedName(entry),
                        kotlinClassLiteral = true,
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

internal fun kotlinClassValuedEntry(start: PsiElement): KtAnnotationEntry? {
    if (PsiTreeUtil.getParentOfType(start, KtStringTemplateExpression::class.java, false) != null) {
        return null
    }
    val entry = PsiTreeUtil.getParentOfType(start, KtAnnotationEntry::class.java) ?: return null
    val qualifiedName = ArmeriaKotlinAnnotationSupport.qualifiedName(entry)
    if (ArmeriaClassValuedAnnotationSupport.expectedInterfaceFqn(qualifiedName) == null) {
        return null
    }
    return entry
}
