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
import com.intellij.openapi.util.Key
import com.linecorp.intellij.plugins.armeria.message
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
        val openedDocService = AtomicBoolean(false)
        val serverStarted = AtomicBoolean(false)
        val urlsRef = AtomicReference<ArmeriaRunServiceUrls?>(null)
        result.processHandler.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) {
                    if (!looksLikeServerStarted(event.text)) {
                        return
                    }
                    if (!serverStarted.compareAndSet(false, true)) {
                        return
                    }
                    openDocServiceIfReady(
                        configuration,
                        urlsRef.get(),
                        openedDocService,
                    )
                }
            },
        )
        ArmeriaRunLaunchInfo.resolveLater(
            project = configuration.project,
            module = configuration.getConfigurationModule().module,
            mainClassFqn = configuration.getMainClass(),
        ) { urls ->
            urlsRef.set(urls)
            printServiceHints(result, urls)
            urls.listen?.let { ArmeriaHttpClientEnvironmentWriter.write(configuration.project, it) }
            if (serverStarted.get()) {
                openDocServiceIfReady(configuration, urls, openedDocService)
            }
        }
        return result
    }

    private fun openDocServiceIfReady(
        configuration: ArmeriaRunConfiguration,
        urls: ArmeriaRunServiceUrls?,
        openedDocService: AtomicBoolean,
    ) {
        if (!configuration.isOpenDocServiceAfterLaunch()) {
            return
        }
        val url = urls?.docService ?: return
        if (!openedDocService.compareAndSet(false, true)) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (!configuration.project.isDisposed) {
                BrowserUtil.browse(url)
            }
        }
    }

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

    companion object {
        internal fun looksLikeServerStarted(text: String?): Boolean {
            if (text.isNullOrBlank()) {
                return false
            }
            return text.contains("Serving HTTP") ||
                text.contains("Serving HTTPS") ||
                text.contains("Started server")
        }
    }
}
