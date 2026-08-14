package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.properties.parsing.PropertiesTokenTypes
import com.intellij.lang.properties.psi.Property
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class ArmeriaSpringBootPropertiesCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val fileName = parameters.originalFile.name
                    if (!ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(fileName)) {
                        return
                    }
                    if (!fileName.endsWith(".properties")) {
                        return
                    }
                    val property =
                        PsiTreeUtil.getParentOfType(parameters.position, Property::class.java, false)
                    val tokenType = parameters.position.node.elementType
                    if (property != null && tokenType == PropertiesTokenTypes.VALUE_CHARACTERS) {
                        val key = property.unescapedKey ?: property.key ?: return
                        if (ArmeriaSpringBootConfigKeys.isIncludeValuePath(key)) {
                            ArmeriaSpringBootCompletionSupport.addIncludeValueCompletions(
                                result,
                                parameters.position.text,
                            )
                        }
                        return
                    }
                    ArmeriaSpringBootCompletionSupport.addPropertiesKeyCompletions(result)
                }
            },
        )
    }
}
