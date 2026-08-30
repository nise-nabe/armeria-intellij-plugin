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
import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
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
        ArmeriaRunFlags.apply(
            params,
            configuration.isVerboseResponses(),
            configuration.isReportBlockedEventLoop(),
        )
        return params
    }

    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
    ): ExecutionResult {
        val result = super.execute(executor, runner)
        val urls = resolveServiceUrls()
        if (urls.listen != null || !urls.isEmpty) {
            result.processHandler.addProcessListener(
                object : ProcessListener {
                    override fun startNotified(event: ProcessEvent) {
                        printServiceHints(result, urls)
                        ApplicationManager.getApplication().invokeLater {
                            if (configuration.project.isDisposed) {
                                return@invokeLater
                            }
                            if (configuration.isOpenDocServiceAfterLaunch()) {
                                urls.docService?.let { BrowserUtil.browse(it) }
                            }
                            urls.listen?.let { ArmeriaHttpClientEnvironmentWriter.write(configuration.project, it) }
                        }
                    }
                },
            )
        }
        return result
    }

    private fun resolveServiceUrls(): ArmeriaRunServiceUrls =
        ArmeriaRunLaunchInfo.resolve(
            project = configuration.project,
            module = configuration.getConfigurationModule().module,
            mainClassFqn = configuration.getMainClass(),
        )

    private fun printServiceHints(
        result: ExecutionResult,
        urls: ArmeriaRunServiceUrls,
    ) {
        val console = result.executionConsole as? ConsoleView ?: return
        printHint(console, message("armeria.run.docService.console.prefix"), urls.docService)
        printHint(console, message("armeria.run.health.console.prefix"), urls.health)
        printHint(console, message("armeria.run.metrics.console.prefix"), urls.metrics)
    }

    private fun printHint(
        console: ConsoleView,
        prefix: String,
        url: String?,
    ) {
        if (url.isNullOrBlank()) {
            return
        }
        console.print(prefix, ConsoleViewContentType.SYSTEM_OUTPUT)
        console.print(" ", ConsoleViewContentType.SYSTEM_OUTPUT)
        console.printHyperlink(url, OpenUrlHyperlinkInfo(url))
        console.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT)
    }
}
