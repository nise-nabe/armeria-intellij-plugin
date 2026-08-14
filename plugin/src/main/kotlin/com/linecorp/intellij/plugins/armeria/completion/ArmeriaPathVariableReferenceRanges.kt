package com.linecorp.intellij.plugins.armeria.completion

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport

internal fun pathVariableRangeInHost(
    host: PsiElement,
    occurrence: ArmeriaPathVariableSupport.PathVariableOccurrence,
): TextRange {
    val valueRange = ElementManipulators.getValueTextRange(host)
    val fallback =
        TextRange(
            valueRange.startOffset + occurrence.startOffset,
            valueRange.startOffset + occurrence.endOffset,
        )
    val injectionHost = host as? PsiLanguageInjectionHost ?: return fallback
    val escaper = injectionHost.createLiteralTextEscaper()
    val relevant = escaper.relevantTextRange
    val decoded = StringBuilder()
    if (!escaper.decode(relevant, decoded)) {
        return fallback
    }
    val start = escaper.getOffsetInHost(occurrence.startOffset, relevant)
    val lastDecoded = (occurrence.endOffset - 1).coerceAtLeast(occurrence.startOffset)
    val last = escaper.getOffsetInHost(lastDecoded, relevant)
    if (start < 0 || last < 0 || last < start) {
        return fallback
    }
    return TextRange(start, last + 1)
}
