package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtNamedFunction

class ArmeriaDuplicateRegistrationKotlinInspection : ArmeriaDuplicateRegistrationInspection() {
    override fun getDisplayName(): String = message("inspection.duplicate.registration.kotlin.display.name")

    override fun highlightElement(element: PsiElement): PsiElement {
        val source = element.navigationElement
        if (source is KtNamedFunction) {
            return source.nameIdentifier ?: source
        }
        return super.highlightElement(element)
    }
}
