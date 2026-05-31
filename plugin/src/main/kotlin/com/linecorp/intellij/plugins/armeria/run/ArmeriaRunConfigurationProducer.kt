package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class ArmeriaRunConfigurationProducer : LazyRunConfigurationProducer<ArmeriaRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return ArmeriaRunConfigurationType().configurationFactories[0]
    }

    override fun setupConfigurationFromContext(
        configuration: ArmeriaRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        // Determine if the context can be used to create an Armeria run configuration
        // For example, check if the file is an Armeria application class
        val element = sourceElement.get() ?: return false
        
        // Check if the element is an Armeria application
        if (isArmeriaApplication(element)) {
            configuration.suggestedName()?.let { configuration.name = it }
            return true
        }
        
        return false
    }

    override fun isConfigurationFromContext(
        configuration: ArmeriaRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        // Check if the configuration matches the context
        val element = context.psiLocation ?: return false
        return isArmeriaApplication(element)
    }
    
    private fun isArmeriaApplication(element: PsiElement): Boolean {
        // Check if the element is an Armeria application
        // This is a simplified implementation - in a real plugin, you would check
        // if the class extends or implements Armeria-specific classes or interfaces
        return element.text.contains("Armeria") || element.text.contains("armeria")
    }
}