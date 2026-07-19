package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.ModuleRootManager
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteContributorBootstrap
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: ArmeriaRunConfiguration,
) : JavaCommandLineState(environment) {
    override fun createJavaParameters(): JavaParameters {
        val params = JavaParameters()
        val mainClass =
            configuration.getMainClass()?.takeIf { it.isNotBlank() }
                ?: throw ExecutionException(message("armeria.run.configuration.main.class.not.specified"))
        params.mainClass = mainClass
        val module =
            configuration.getConfigurationModule().module
                ?: throw ExecutionException(message("armeria.run.configuration.module.not.specified"))
        JavaParametersUtil.configureModule(module, params, JavaParameters.JDK_AND_CLASSES, null)
        params.workingDirectory = params.workingDirectory
            ?: ModuleRootManager
                .getInstance(module)
                .contentRoots
                .firstOrNull()
                ?.path
            ?: configuration.project.basePath
        return params
    }

    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
    ): ExecutionResult {
        val result = super.execute(executor, runner)
        val docServiceUrl = resolveDocServiceUrl()
        if (docServiceUrl != null) {
            result.processHandler.addProcessListener(
                object : ProcessListener {
                    override fun startNotified(event: ProcessEvent) {
                        printDocServiceHint(result, docServiceUrl)
                    }
                },
            )
        }
        return result
    }

    private fun resolveDocServiceUrl(): String? {
        val project = configuration.project
        val module = configuration.getConfigurationModule().module ?: return null
        if (DumbService.isDumb(project)) {
            return null
        }
        return try {
            ArmeriaRouteContributorBootstrap.ensureRegistered()
            val routes =
                ReadAction.compute<List<ArmeriaRoute>, RuntimeException> {
                    ArmeriaRouteCollector.collect(project)
                }
            ArmeriaDocServiceSupport.primaryUrl(routes.filter { it.moduleName == module.name })
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun printDocServiceHint(
        result: ExecutionResult,
        url: String,
    ) {
        val console = result.executionConsole as? ConsoleView ?: return
        console.print(message("armeria.run.docService.console.prefix"), ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print(" ", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.printHyperlink(url, OpenUrlHyperlinkInfo(url))
        console.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }
}
