package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner

class ArmeriaRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: ArmeriaRunConfiguration
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        // In a real implementation, this would start the Armeria application
        // For now, we'll just create a dummy process handler
        val commandLine = createCommandLine()
        val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }

    private fun createCommandLine(): com.intellij.execution.configurations.GeneralCommandLine {
        val commandLine = com.intellij.execution.configurations.GeneralCommandLine()
        commandLine.exePath = "java"

        // Set working directory to project base path
        commandLine.workDirectory = java.io.File(configuration.project.basePath ?: ".")

        // Add main class
        val mainClass = configuration.getMainClass()
        if (!mainClass.isNullOrEmpty()) {
            commandLine.addParameter(mainClass)
        } else {
            throw ExecutionException("Main class is not specified")
        }

        return commandLine
    }

    override fun execute(executor: com.intellij.execution.Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        if (console != null) {
            console.attachToProcess(processHandler)
        }
        return DefaultExecutionResult(console, processHandler)
    }
}
