package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet

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
        val lineToCaret = ArmeriaSpringBootCompletionSupport.lineToCaret(parameters)
        val separator = ArmeriaSpringBootCompletionSupport.propertiesValueSeparatorIndex(lineToCaret)
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
}
