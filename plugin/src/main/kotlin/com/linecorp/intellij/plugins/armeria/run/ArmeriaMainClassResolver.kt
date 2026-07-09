package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport

internal object ArmeriaMainClassResolver {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun findArmeriaMainClass(element: PsiElement?): PsiClass? {
        element ?: return null
        val file = element.containingFile ?: return null
        if (!ArmeriaRouteSupport.referencesArmeriaApplicationInSource(file.viewProvider.contents)) {
            return null
        }

        PsiTreeUtil.getParentOfType(element, false, PsiClass::class.java)
            ?.takeIf(::isJvmMainClass)
            ?.let { return it }

        if (!isKotlinPluginAvailable()) {
            return null
        }
        return ArmeriaKotlinMainClassSupport.findMainClass(element)
    }

    private fun isJvmMainClass(psiClass: PsiClass): Boolean =
        PsiMethodUtil.hasMainInClass(psiClass) ||
            PsiMethodUtil.hasMainMethod(psiClass) ||
            PsiMethodUtil.findMainMethod(psiClass) != null

    private fun isKotlinPluginAvailable(): Boolean =
        PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
