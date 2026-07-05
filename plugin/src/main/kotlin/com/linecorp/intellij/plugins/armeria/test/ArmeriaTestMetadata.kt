package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaTestMetadata {
    fun moduleName(element: PsiElement): String {
        return ModuleUtilCore.findModuleForPsiElement(element)?.name
            ?: message("route.explorer.module.unassigned")
    }
}
