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
                    // Path of the mapping that contains the key under edit (not the leaf itself).
                    val completionPath = ArmeriaSpringBootConfigSupport.completionContextPath(yamlKeyPath(keyValue))
                    if (!ArmeriaSpringBootConfigKeys.isRelevantCompletionPath(completionPath)) {
                        return
                    }
                    val seenInsertTexts = linkedSetOf<String>()
                    for (suggestion in ArmeriaSpringBootConfigKeys.COMPLETION_SUGGESTIONS) {
                        val insertText =
                            ArmeriaSpringBootConfigKeys.completionInsertText(completionPath, suggestion)
                                ?: continue
                        if (!seenInsertTexts.add(insertText)) {
                            continue
                        }
                        if (!result.prefixMatcher.prefixMatches(insertText)) {
                            continue
                        }
                        val doc = ArmeriaSpringBootConfigKeys.documentationFor(suggestion)
                        var element = LookupElementBuilder.create(insertText)
                        if (completionPath.isNotEmpty()) {
                            element = element.withTypeText(suggestion)
                        }
                        if (!doc.isNullOrEmpty()) {
                            element = element.withTailText(" — $doc", true)
                        }
                        result.addElement(element)
                    }
                }
            },
        )
    }

    /**
     * Full dotted path of [keyValue], including list indexes (e.g. `armeria.ports[0].port`).
     *
     * Walks via the immediate PSI parent ([YAMLMapping] / sequence item), not `parent.parent`,
     * so nested block mappings continue through their owning [YAMLKeyValue].
     */
    private fun yamlKeyPath(keyValue: YAMLKeyValue): String {
        val segments = mutableListOf<String>()
        var current: YAMLKeyValue? = keyValue
        while (current != null) {
            current.keyText.takeIf { it.isNotBlank() }?.let { segments.add(0, it) }
            when (val container = current.parent) {
                is YAMLMapping -> {
                    when (val owner = container.parent) {
                        is YAMLKeyValue -> {
                            current = owner
                        }
                        is YAMLSequenceItem -> {
                            val sequence = owner.parent as? YAMLSequence
                            val index = sequence?.items?.indexOf(owner) ?: -1
                            val seqKey = sequence?.parent as? YAMLKeyValue
                            if (index >= 0 && seqKey?.keyText != null) {
                                segments.add(0, "${seqKey.keyText}[$index]")
                                current =
                                    when (val seqParent = seqKey.parent) {
                                        is YAMLMapping -> seqParent.parent as? YAMLKeyValue
                                        else -> null
                                    }
                            } else {
                                current = null
                            }
                        }
                        else -> current = null
                    }
                }
                else -> current = null
            }
        }
        return segments.joinToString(".")
    }
}
