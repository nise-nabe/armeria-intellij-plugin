package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.roots.ModuleRootManager
import java.io.File

class ArmeriaRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: ArmeriaRunConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val params = createJavaParameters()
        val module = configuration.getConfigurationModule().module
        val commandLine = GeneralCommandLine()
        commandLine.exePath = params.jdk?.homePath?.let { java.io.File(it, "bin/java").path } ?: "java"
        commandLine.addParameters(params.vmParametersList.parameters)
        if (params.classPath.pathList.isNotEmpty()) {
            commandLine.addParameters("-classpath", params.classPath.pathList.joinToString(File.pathSeparator))
        }
        commandLine.addParameters(params.mainClass)
        commandLine.addParameters(params.programParametersList.parameters)
        commandLine.workDirectory = params.workingDirectory?.let(::File)
            ?: module?.let { ModuleRootManager.getInstance(it).contentRoots.firstOrNull()?.path?.let(::File) }
            ?: File(configuration.project.basePath ?: ".")
        val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    private fun createJavaParameters(): JavaParameters {
        val params = JavaParameters()
        val mainClass = configuration.getMainClass()
            ?: throw ExecutionException("Main class is not specified")
        params.mainClass = mainClass
        val module = configuration.getConfigurationModule().module
        if (module != null) {
            JavaParametersUtil.configureModule(module, params, JavaParameters.CLASSES_AND_TESTS, null)
        }
        return params
    }
}
