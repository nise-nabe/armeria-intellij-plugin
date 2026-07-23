package com.linecorp.intellij.plugins.armeria.test

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

data class ArmeriaJUnitServerExtension(
    val variableName: String,
    val containingClassName: String,
    val moduleName: String,
    val pointer: SmartPsiElementPointer<PsiElement>,
) {
    companion object {
        fun create(
            element: PsiElement,
            variableName: String,
            containingClassName: String,
            moduleName: String,
        ): ArmeriaJUnitServerExtension =
            ArmeriaJUnitServerExtension(
                variableName = variableName,
                containingClassName = containingClassName,
                moduleName = moduleName,
                pointer = SmartPointerManager.createPointer(element),
            )
    }
}
