package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: ArmeriaRunConfiguration,
) : JavaCommandLineState(environment) {

    override fun createJavaParameters(): JavaParameters {
        val params = JavaParameters()
        val mainClass = configuration.getMainClass()?.takeIf { it.isNotBlank() }
            ?: throw ExecutionException(message("armeria.run.configuration.main.class.not.specified"))
        params.mainClass = mainClass
        val module = configuration.getConfigurationModule().module
            ?: throw ExecutionException(message("armeria.run.configuration.module.not.specified"))
        JavaParametersUtil.configureModule(module, params, JavaParameters.JDK_AND_CLASSES, null)
        params.workingDirectory = params.workingDirectory
            ?: ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path
            ?: configuration.project.basePath
        return params
    }
}
