package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<ArmeriaRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): ArmeriaRunConfigurationOptions {
        return super.getOptions() as ArmeriaRunConfigurationOptions
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return ArmeriaRunProfileState(environment, this)
    }

    fun getMainClass(): String? = options.getMainClass()

    fun setMainClass(mainClass: String?) {
        options.setMainClass(mainClass)
    }

    override fun getConfigurationEditor() = ArmeriaRunConfigurationEditor(project)

    @NlsActions.ActionText
    override fun suggestedName(): String? {
        return message("armeria.run.configuration.name")
    }
}
