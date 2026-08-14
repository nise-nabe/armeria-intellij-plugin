package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.util.TextRange

class ArmeriaSpringBootPropertiesCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val fileName = parameters.originalFile.name
        if (!ArmeriaSpringBootConfigSupport.isApplicationConfigFileName(fileName)) {
            return
        }
        if (!fileName.endsWith(".properties")) {
            return
        }
        val lineToCaret = lineToCaret(parameters)
        val separator = lineToCaret.indexOfFirst { it == '=' || it == ':' }
        if (separator >= 0) {
            val key =
                ArmeriaSpringBootCompletionSupport.stripDummy(lineToCaret.substring(0, separator).trim())
            if (ArmeriaSpringBootConfigKeys.isIncludeValuePath(key)) {
                ArmeriaSpringBootCompletionSupport.addIncludeValueCompletions(
                    result,
                    lineToCaret.substring(separator + 1),
                )
            }
            return
        }
        ArmeriaSpringBootCompletionSupport.addPropertiesKeyCompletions(result)
    }

    private fun lineToCaret(parameters: CompletionParameters): String {
        val document = parameters.editor.document
        val offset = parameters.offset.coerceAtMost(document.textLength)
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        return document.getText(TextRange(lineStart, offset))
    }
}
