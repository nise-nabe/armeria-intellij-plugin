package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YAMLScalar

class ArmeriaSpringBootYamlCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(YAMLScalar::class.java).withParent(YAMLKeyValue::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(p: CompletionParameters, ctx: ProcessingContext, result: CompletionResultSet) {
                    val fileName = p.originalFile.name
                    if (!ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(fileName)) {
                        return
                    }
                    val keyValue = p.position.parent as? YAMLKeyValue ?: return
                    val keyPath = yamlKeyPath(keyValue)
                    if (!isRelevantKeyPath(keyPath)) {
                        return
                    }
                    for (suggestion in ArmeriaSpringBootConfigKeys.COMPLETION_SUGGESTIONS) {
                        val doc = ArmeriaSpringBootConfigKeys.documentationFor(suggestion).orEmpty()
                        when {
                            keyPath == "armeria" && suggestion.startsWith("armeria.") -> {
                                result.addElement(
                                    LookupElementBuilder.create(suggestion.substringAfterLast('.'))
                                        .withTypeText(suggestion)
                                        .withTailText(" — $doc", true),
                                )
                            }
                            suggestion.startsWith("$keyPath.") && result.prefixMatcher.prefixMatches(suggestion) -> {
                                result.addElement(
                                    LookupElementBuilder.create(suggestion.substringAfterLast('.'))
                                        .withTypeText(suggestion)
                                        .withTailText(" — $doc", true),
                                )
                            }
                            keyPath.isEmpty() && result.prefixMatcher.prefixMatches(suggestion) -> {
                                result.addElement(LookupElementBuilder.create(suggestion).withTailText(" — $doc", true))
                            }
                        }
                    }
                }
            },
        )
    }

    private fun isRelevantKeyPath(keyPath: String): Boolean {
        if (keyPath.isEmpty()) {
            return true
        }
        if (keyPath == "armeria" || keyPath.startsWith("armeria.")) {
            return true
        }
        return keyPath in ArmeriaSpringBootConfigKeys.RELATED_ROOT_KEYS ||
            keyPath.startsWith("server") ||
            keyPath.startsWith("management") ||
            keyPath.startsWith("spring.main")
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
