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
import org.jetbrains.yaml.psi.YAMLSequenceItem

class ArmeriaSpringBootYamlCompletionContributor : CompletionContributor() {
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
                            return
                        }
                        val keyPrefix =
                            ArmeriaSpringBootCompletionSupport.incompleteYamlKeyPrefix(
                                ArmeriaSpringBootCompletionSupport.lineToCaret(parameters),
                            ) ?: return
                        val completionPath =
                            ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(target.path)
                        if (!ArmeriaSpringBootConfigKeys.isRelevantCompletionPath(completionPath)) {
                            return
                        }
                        val prefixed =
                            if (keyPrefix.isEmpty()) {
                                result
                            } else {
                                result.withPrefixMatcher(keyPrefix)
                            }
                        ArmeriaSpringBootCompletionSupport.addYamlKeyCompletions(prefixed, completionPath)
                        return
                    }
                    val completionPath =
                        if (target.stripLeaf) {
                            ArmeriaSpringBootConfigSupport.completionContextPath(target.path)
                        } else {
                            ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(target.path)
                        }
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
        val stripLeaf: Boolean = !isValue,
    )

    private fun yamlCompletionTarget(parameters: CompletionParameters): YamlCompletionTarget? {
        val position = parameters.position
        val offset = parameters.offset
        when (val parent = position.parent) {
            is YAMLKeyValue -> {
                val isKey = parent.key?.textRange?.contains(offset) == true
                return YamlCompletionTarget(ArmeriaSpringBootYamlPsiSupport.yamlKeyPath(parent), isValue = !isKey)
            }
            is YAMLSequenceItem -> {
                return YamlCompletionTarget(
                    ArmeriaSpringBootYamlPsiSupport.yamlSequenceItemPath(parent),
                    isValue = true,
                )
            }
            else -> {
                val keyValue = PsiTreeUtil.getParentOfType(position, YAMLKeyValue::class.java, false)
                if (keyValue != null) {
                    val isKey = keyValue.key?.textRange?.contains(offset) == true
                    return YamlCompletionTarget(
                        ArmeriaSpringBootYamlPsiSupport.yamlKeyPath(keyValue),
                        isValue = !isKey,
                    )
                }
                val sequenceItem = PsiTreeUtil.getParentOfType(position, YAMLSequenceItem::class.java, false)
                if (sequenceItem != null) {
                    return YamlCompletionTarget(
                        ArmeriaSpringBootYamlPsiSupport.yamlSequenceItemPath(sequenceItem),
                        isValue = true,
                    )
                }
                val mapping = PsiTreeUtil.getParentOfType(position, YAMLMapping::class.java, false)
                val owner = mapping?.parent as? YAMLKeyValue
                if (owner != null) {
                    return YamlCompletionTarget(
                        ArmeriaSpringBootYamlPsiSupport.yamlKeyPath(owner),
                        isValue = false,
                        stripLeaf = false,
                    )
                }
                return YamlCompletionTarget("", isValue = false, stripLeaf = false)
            }
        }
    }
}
