package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class ArmeriaSpringBootYamlCompletionContributor : CompletionContributor() {
    init {
        extend(PlatformPatterns.psiElement(YAMLScalar::class.java).withParent(YAMLKeyValue::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(p: CompletionParameters, ctx: ProcessingContext, result: CompletionResultSet) {
                    val keyText = (p.position.parent as? YAMLKeyValue)?.keyText?.text ?: return
                    if (keyText != "armeria" && !keyText.startsWith("armeria.") && keyText !in ArmeriaSpringBootConfigKeys.RELATED_ROOT_KEYS &&
                        !keyText.startsWith("server") && !keyText.startsWith("management") && !keyText.startsWith("spring.main")) return
                    for (s in ArmeriaSpringBootConfigKeys.COMPLETION_SUGGESTIONS) {
                        val doc = ArmeriaSpringBootConfigKeys.documentationFor(s).orEmpty()
                        if (s.startsWith("armeria.") && keyText == "armeria") {
                            result.addElement(LookupElementBuilder.create(s.substringAfterLast('.')).withTypeText(s).withTailText(" — $doc", true))
                        } else if (result.prefixMatcher.prefixMatches(s)) {
                            result.addElement(LookupElementBuilder.create(s).withTailText(" — $doc", true))
                        }
                    }
                }
            })
    }
}
