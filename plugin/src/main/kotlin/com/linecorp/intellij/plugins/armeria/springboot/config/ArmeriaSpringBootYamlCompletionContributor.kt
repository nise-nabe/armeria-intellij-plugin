package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

class ArmeriaSpringBootYamlCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(YAMLScalar::class.java).withParent(YAMLKeyValue::class.java),
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
                    val keyValue = parameters.position.parent as? YAMLKeyValue ?: return
                    val key = keyValue.key ?: return
                    if (!key.textRange.contains(parameters.offset)) {
                        return
                    }
                    val keyPath = yamlKeyPath(keyValue)
                    if (!ArmeriaSpringBootConfigKeys.isRelevantCompletionPath(keyPath)) {
                        return
                    }
                    val completionPath = ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(keyPath)
                    val seenInsertTexts = linkedSetOf<String>()
                    for (suggestion in ArmeriaSpringBootConfigKeys.COMPLETION_SUGGESTIONS) {
                        val insertText = ArmeriaSpringBootConfigKeys.completionInsertText(completionPath, suggestion)
                            ?: continue
                        if (!seenInsertTexts.add(insertText)) {
                            continue
                        }
                        if (!result.prefixMatcher.prefixMatches(insertText)) {
                            continue
                        }
                        val doc = ArmeriaSpringBootConfigKeys.documentationFor(suggestion).orEmpty()
                        val element = if (completionPath.isEmpty()) {
                            LookupElementBuilder.create(insertText).withTailText(" — $doc", true)
                        } else {
                            LookupElementBuilder.create(insertText)
                                .withTypeText(suggestion)
                                .withTailText(" — $doc", true)
                        }
                        result.addElement(element)
                    }
                }
            },
        )
    }

    private fun yamlKeyPath(keyValue: YAMLKeyValue): String {
        val segments = mutableListOf<String>()
        var current: YAMLKeyValue? = keyValue
        while (current != null) {
            current.keyText?.takeIf { it.isNotBlank() }?.let { segments.add(0, it) }
            val parent = current.parent?.parent
            current = when (parent) {
                is YAMLMapping -> parent.parent as? YAMLKeyValue
                is YAMLSequenceItem -> {
                    val sequence = parent.parent as? YAMLSequence
                    val index = sequence?.items?.indexOf(parent) ?: -1
                    val container = sequence?.parent as? YAMLKeyValue
                    if (index >= 0 && container?.keyText != null) {
                        segments.add(0, "${container.keyText}[$index]")
                    }
                    container?.parent?.parent as? YAMLKeyValue
                }
                else -> null
            }
        }
        return segments.joinToString(".")
    }
}
