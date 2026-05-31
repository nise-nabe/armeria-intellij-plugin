package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaRunConfigurationType : ConfigurationTypeBase(
    ID,
    message("armeria.run.configuration.name"),
    message("armeria.run.configuration.description"),
    ArmeriaIcons.Armeria
) {
    init {
        addFactory(ArmeriaRunConfigurationFactory(this))
    }

    companion object {
        const val ID = "ArmeriaRunConfiguration"
    }
}
