package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project

class ArmeriaRunConfigurationFactory(
    type: ConfigurationType,
) : ConfigurationFactory(type) {
    override fun getId(): String = ArmeriaRunConfigurationType.ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration = ArmeriaRunConfiguration(project, this, "Armeria")

    override fun getOptionsClass(): Class<out BaseState>? = ArmeriaRunConfigurationOptions::class.java
}
