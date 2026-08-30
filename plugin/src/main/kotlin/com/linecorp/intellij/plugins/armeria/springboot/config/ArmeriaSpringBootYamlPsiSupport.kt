package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.spring.SpringArmeriaConfigSemantics
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

internal object ArmeriaSpringBootYamlPsiSupport {
    /**
     * Full dotted path of [keyValue], including list indexes (e.g. `armeria.ports[0].port`).
     *
     * Walks via the immediate PSI parent ([YAMLMapping] / sequence item), not `parent.parent`,
     * so nested block mappings continue through their owning [YAMLKeyValue].
     */
    fun yamlKeyPath(keyValue: YAMLKeyValue): String {
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

    fun yamlSequenceItemPath(item: YAMLSequenceItem): String {
        val sequence = item.parent as? YAMLSequence ?: return ""
        val index = sequence.items.indexOf(item)
        val seqKey = sequence.parent as? YAMLKeyValue ?: return ""
        val base = yamlKeyPath(seqKey)
        return if (index >= 0) "$base[$index]" else base
    }

    fun highlightForPath(
        file: PsiFile,
        normalizedPath: String,
    ): PsiElement? {
        val keyValues = yamlKeyValues(file)
        val match =
            keyValues.firstOrNull { keyValue ->
                ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(yamlKeyPath(keyValue)) == normalizedPath
            } ?: return null
        return highlightElement(match)
    }

    fun highlightIncludeToken(
        file: PsiFile,
        includeId: String,
    ): PsiElement? {
        for (keyValue in yamlKeyValues(file)) {
            if (!ArmeriaSpringBootConfigKeys.isIncludeValuePath(yamlKeyPath(keyValue))) {
                continue
            }
            when (val value = keyValue.value) {
                is YAMLSequence -> {
                    for (item in value.items) {
                        val scalar = item.value as? YAMLScalar ?: continue
                        if (includeTokenMatches(scalar.textValue, includeId)) {
                            return scalar
                        }
                    }
                    return highlightElement(keyValue)
                }
                is YAMLScalar -> return value
                else -> return highlightElement(keyValue)
            }
        }
        return null
    }

    private fun includeTokenMatches(
        raw: String,
        includeId: String,
    ): Boolean {
        val tokens = SpringArmeriaConfigSemantics.parseIncludeTokens(raw)
        return includeId in SpringArmeriaConfigSemantics.expandIncludes(tokens)
    }

    private fun highlightElement(keyValue: YAMLKeyValue): PsiElement = keyValue.value ?: keyValue.key ?: keyValue

    private fun yamlKeyValues(file: PsiFile): Collection<YAMLKeyValue> {
        val yamlFile = file as? YAMLFile ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java)
    }
}
