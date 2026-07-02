package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfigurationModule
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.linecorp.intellij.plugins.armeria.message
import org.jdom.Element

class ArmeriaRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : LocatableConfigurationBase<ArmeriaRunConfigurationOptions>(project, factory, name) {

    private val runConfigurationModule = JavaRunConfigurationModule(project, true)

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

    fun getConfigurationModule(): RunConfigurationModule = runConfigurationModule

    fun setModuleModule(module: Module?) {
        runConfigurationModule.module = module
        options.moduleName.setValue(options, module?.name)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        val moduleName = options.moduleName.getValue(options)
        if (!moduleName.isNullOrBlank()) {
            runConfigurationModule.setModuleName(moduleName)
        }
    }

    override fun writeExternal(element: Element) {
        options.moduleName.setValue(options, runConfigurationModule.moduleName)
        super.writeExternal(element)
    }

    override fun getConfigurationEditor() = ArmeriaRunConfigurationEditor(project)

    @NlsActions.ActionText
    override fun suggestedName(): String {
        val mainClass = getMainClass()?.substringAfterLast('.')
        return if (mainClass.isNullOrBlank()) {
            message("armeria.run.configuration.name")
        } else {
            message("armeria.run.configuration.suggested.name", mainClass)
        }
    }
}
