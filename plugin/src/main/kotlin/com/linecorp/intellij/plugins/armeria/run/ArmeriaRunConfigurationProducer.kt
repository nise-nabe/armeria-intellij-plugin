package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class ArmeriaRunConfigurationProducer : LazyRunConfigurationProducer<ArmeriaRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationType.CONFIGURATION_TYPE_EP
            .findExtension(ArmeriaRunConfigurationType::class.java)
            ?.configurationFactories
            ?.first()
            ?: ArmeriaRunConfigurationType().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: ArmeriaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val mainClass = ArmeriaMainClassResolver.findArmeriaMainClass(context.psiLocation) ?: return false
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
        val mainClass = ArmeriaMainClassResolver.findArmeriaMainClass(context.psiLocation) ?: return false
        if (mainClass.qualifiedName != configuration.getMainClass()) {
            return false
        }
        val contextModule = ModuleUtilCore.findModuleForPsiElement(mainClass)
        return contextModule == configuration.getConfigurationModule().module
    }
}
