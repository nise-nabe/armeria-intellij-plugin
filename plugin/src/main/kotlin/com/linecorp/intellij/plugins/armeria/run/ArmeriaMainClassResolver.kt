package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypes
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

    private fun isJvmMainClass(psiClass: PsiClass): Boolean {
        if (PsiMethodUtil.hasMainInClass(psiClass) ||
            PsiMethodUtil.hasMainMethod(psiClass) ||
            PsiMethodUtil.findMainMethod(psiClass) != null
        ) {
            return true
        }
        // Light fixtures may leave String[] unresolved, so PsiMethodUtil can miss a valid
        // classic main. Accept only void-returning static mains with a JVM-compatible shape.
        return psiClass.methods.any(::looksLikeJvmMainMethod)
    }

    private fun looksLikeJvmMainMethod(method: PsiMethod): Boolean {
        if (method.name != "main" || !method.hasModifierProperty(PsiModifier.STATIC)) {
            return false
        }
        val returnType = method.returnType
        if (returnType != null && returnType != PsiTypes.voidType()) {
            return false
        }
        val parameters = method.parameterList.parameters
        return when (parameters.size) {
            0 -> true
            1 -> {
                val typeText = parameters[0].type.presentableText
                typeText == "String[]" || typeText == "String..." || typeText.endsWith("String[]")
            }
            else -> false
        }
    }

    private fun isKotlinPluginAvailable(): Boolean =
        PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
