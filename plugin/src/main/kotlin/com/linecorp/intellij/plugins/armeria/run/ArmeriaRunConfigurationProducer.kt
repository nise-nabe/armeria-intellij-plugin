package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport

class ArmeriaRunConfigurationProducer : LazyRunConfigurationProducer<ArmeriaRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return ArmeriaRunConfigurationType().configurationFactories[0]
    }

    override fun setupConfigurationFromContext(
        configuration: ArmeriaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val mainClass = findArmeriaMainClass(context.psiLocation) ?: return false
        val qualifiedName = mainClass.qualifiedName ?: return false
        val module = ModuleUtilCore.findModuleForPsiElement(mainClass) ?: return false
        configuration.setMainClass(qualifiedName)
        configuration.setModule(module)
        configuration.name = configuration.suggestedName()
        sourceElement.set(mainClass)
        return true
    }

    override fun isConfigurationFromContext(
        configuration: ArmeriaRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val mainClass = findArmeriaMainClass(context.psiLocation) ?: return false
        if (mainClass.qualifiedName != configuration.getMainClass()) {
            return false
        }
        val contextModule = ModuleUtilCore.findModuleForPsiElement(mainClass)
        return contextModule == configuration.getConfigurationModule().module
    }

    private fun findArmeriaMainClass(element: PsiElement?): PsiClass? {
        element ?: return null
        val containingClass = PsiTreeUtil.getParentOfType(element, false, PsiClass::class.java)
            ?: return null
        if (!PsiMethodUtil.hasMainInClass(containingClass)) {
            return null
        }
        val file = element.containingFile ?: return null
        if (!referencesArmeria(file.text)) {
            return null
        }
        return containingClass
    }

    private fun referencesArmeria(source: String): Boolean {
        return ArmeriaRouteSupport.referencesArmeriaInText(source) ||
            ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(source)
    }
}
