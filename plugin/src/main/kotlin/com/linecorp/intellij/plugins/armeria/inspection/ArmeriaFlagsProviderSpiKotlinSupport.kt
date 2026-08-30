package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

internal object ArmeriaFlagsProviderSpiKotlinSupport {
    fun highlight(declaration: KtClassOrObject): PsiElement? {
        if (declaration.name == null) {
            return null
        }
        if (declaration is KtClass && (declaration.isInterface() || declaration.isEnum() || declaration.isAnnotation())) {
            return null
        }
        val psiClass = ArmeriaKotlinInspectionCallChains.toPsiClass(declaration)
        if (psiClass != null) {
            return ArmeriaFlagsProviderSpiSupport.highlight(psiClass)?.let {
                declaration.nameIdentifier ?: declaration
            }
        }
        val looksLikeFlagsProvider =
            ArmeriaKotlinInspectionCallChains.superTypeSimpleNames(declaration).any { name ->
                name == "FlagsProvider" || name == ArmeriaProductionChecklist.FLAGS_PROVIDER_CLASS
            }
        if (!looksLikeFlagsProvider) {
            return null
        }
        val fqcn = declaration.fqName?.asString() ?: return null
        if (ArmeriaProductionChecklist.isFlagsProviderRegistered(declaration, fqcn)) {
            return null
        }
        return declaration.nameIdentifier ?: declaration
    }
}
