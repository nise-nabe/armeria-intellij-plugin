package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
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
            PlatformPatterns.psiElement(YAMLScalar::class.java),
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
                    val target = yamlCompletionTarget(parameters) ?: return
                    if (!ArmeriaSpringBootConfigKeys.isRelevantCompletionPath(target.path)) {
                        return
                    }
                    if (target.isValue) {
                        if (ArmeriaSpringBootConfigKeys.isIncludeValuePath(target.path)) {
                            ArmeriaSpringBootCompletionSupport.addIncludeValueCompletions(
                                result,
                                parameters.position.text,
                            )
                        }
                        return
                    }
                    val completionPath = ArmeriaSpringBootConfigSupport.completionContextPath(target.path)
                    if (!ArmeriaSpringBootConfigKeys.isRelevantCompletionPath(completionPath)) {
                        return
                    }
                    ArmeriaSpringBootCompletionSupport.addYamlKeyCompletions(result, completionPath)
                }
            },
        )
    }

    private data class YamlCompletionTarget(
        val path: String,
        val isValue: Boolean,
    )

    private fun yamlCompletionTarget(parameters: CompletionParameters): YamlCompletionTarget? {
        val position = parameters.position
        val offset = parameters.offset
        when (val parent = position.parent) {
            is YAMLKeyValue -> {
                val isKey = parent.key?.textRange?.contains(offset) == true
                return YamlCompletionTarget(yamlKeyPath(parent), isValue = !isKey)
            }
            is YAMLSequenceItem -> {
                return YamlCompletionTarget(yamlSequenceItemPath(parent), isValue = true)
            }
            else -> {
                val keyValue = PsiTreeUtil.getParentOfType(position, YAMLKeyValue::class.java, false)
                if (keyValue != null) {
                    val isKey = keyValue.key?.textRange?.contains(offset) == true
                    return YamlCompletionTarget(yamlKeyPath(keyValue), isValue = !isKey)
                }
                val sequenceItem = PsiTreeUtil.getParentOfType(position, YAMLSequenceItem::class.java, false)
                if (sequenceItem != null) {
                    return YamlCompletionTarget(yamlSequenceItemPath(sequenceItem), isValue = true)
                }
                return null
            }
        }
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

    private fun yamlSequenceItemPath(item: YAMLSequenceItem): String {
        val sequence = item.parent as? YAMLSequence ?: return ""
        val index = sequence.items.indexOf(item)
        val seqKey = sequence.parent as? YAMLKeyValue ?: return ""
        val base = yamlKeyPath(seqKey)
        return if (index >= 0) "$base[$index]" else base
    }
}
