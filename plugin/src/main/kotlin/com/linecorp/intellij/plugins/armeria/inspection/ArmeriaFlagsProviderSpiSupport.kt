package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.InheritanceUtil

internal object ArmeriaFlagsProviderSpiSupport {
    fun highlight(psiClass: PsiClass): PsiElement? {
        if (!isInspectableImplementation(psiClass)) {
            return null
        }
        return try {
            if (ArmeriaProductionChecklist.isFlagsProviderRegistered(psiClass)) {
                null
            } else {
                psiClass.nameIdentifier ?: psiClass
            }
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun isInspectableImplementation(psiClass: PsiClass): Boolean {
        if (!psiClass.isPhysical || psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) {
            return false
        }
        if (psiClass.name == null || psiClass.qualifiedName == ArmeriaProductionChecklist.FLAGS_PROVIDER_CLASS) {
            return false
        }
        if (psiClass.modifierList?.hasModifierProperty(PsiModifier.PRIVATE) == true) {
            return false
        }
        return InheritanceUtil.isInheritor(psiClass, ArmeriaProductionChecklist.FLAGS_PROVIDER_CLASS)
    }
}
