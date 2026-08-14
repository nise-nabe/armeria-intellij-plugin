package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange

internal object ArmeriaSpringBootCompletionSupport {
    fun addYamlKeyCompletions(
        result: CompletionResultSet,
        completionPath: String,
    ) {
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
            result.addElement(keyLookup(insertText, suggestion, showTypeText = completionPath.isNotEmpty()))
        }
    }

    fun addPropertiesKeyCompletions(result: CompletionResultSet) {
        for (suggestion in ArmeriaSpringBootConfigKeys.COMPLETION_SUGGESTIONS) {
            if (suggestion == ArmeriaSpringBootConfigKeys.ARMERIA_ROOT) {
                continue
            }
            if (!result.prefixMatcher.prefixMatches(suggestion)) {
                continue
            }
            result.addElement(keyLookup(suggestion, suggestion, showTypeText = false))
        }
    }

    fun addIncludeValueCompletions(
        result: CompletionResultSet,
        rawValue: String,
    ) {
        val token = lastIncludeToken(rawValue)
        val prefixed = result.withPrefixMatcher(token)
        for (id in ArmeriaSpringBootConfigKeys.INTERNAL_SERVICE_INCLUDE_VALUES) {
            if (token.isNotEmpty() && !id.startsWith(token, ignoreCase = true)) {
                continue
            }
            val doc = ArmeriaSpringBootConfigKeys.documentationForIncludeValue(id)
            var element = LookupElementBuilder.create(id)
            if (!doc.isNullOrEmpty()) {
                element = element.withTailText(" — $doc", true)
            }
            prefixed.addElement(element)
        }
    }

    fun lastIncludeToken(rawValue: String): String {
        val sanitized = stripDummy(rawValue)
        return sanitized.substringAfterLast(',').trim()
    }

    fun lineToCaret(parameters: CompletionParameters): String {
        val document = parameters.editor.document
        val offset = parameters.offset.coerceAtMost(document.textLength)
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        return document.getText(TextRange(lineStart, offset))
    }

    /**
     * Index of the `.properties` key/value delimiter in [lineToCaret], or `-1` while still
     * typing the key. `Properties.load` accepts `=`, `:`, or whitespace.
     */
    fun propertiesValueSeparatorIndex(lineToCaret: String): Int {
        val explicit = lineToCaret.indexOfFirst { it == '=' || it == ':' }
        if (explicit >= 0) {
            return explicit
        }
        return lineToCaret.indexOfFirst { it.isWhitespace() }
    }

    /**
     * Prefix of a nested YAML key being typed on its own line (no `:` yet). Null when the
     * caret is in a real value (`key: value` or `include: …`).
     */
    fun incompleteYamlKeyPrefix(lineToCaret: String): String? {
        var content = stripDummy(lineToCaret)
        if (content.startsWith("-")) {
            content = content.removePrefix("-").trim()
        }
        if (content.contains(':') || content.contains('=')) {
            return null
        }
        return content
    }

    fun stripDummy(raw: String): String =
        raw
            .replace(CompletionUtilCore.DUMMY_IDENTIFIER, "")
            .replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")
            .trim()

    private fun keyLookup(
        insertText: String,
        suggestion: String,
        showTypeText: Boolean,
    ): LookupElementBuilder {
        var element = LookupElementBuilder.create(insertText)
        if (showTypeText) {
            element = element.withTypeText(suggestion)
        }
        val doc = ArmeriaSpringBootConfigKeys.documentationFor(suggestion)
        if (!doc.isNullOrEmpty()) {
            element = element.withTailText(" — $doc", true)
        }
        return element
    }
}
