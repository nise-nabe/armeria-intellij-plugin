package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.AccessToken
import com.intellij.testFramework.LoggedErrorProcessor

/**
 * IDEA Ultimate 2026.2.2 ships `com.intellij.modules.ultimate` with a ZKM-obfuscated
 * [com.intellij.openapi.startup.ProjectActivity] (`B.B.B.B.s`) whose constructor is not
 * injectable in headless fixture tests. The platform logs a [com.intellij.diagnostic.PluginException]
 * during project open, and [com.intellij.testFramework.TestLoggerFactory] fails the test.
 */
private object UltimatePostStartupLoggedErrorProcessor : LoggedErrorProcessor() {
    override fun processError(
        category: String,
        message: String,
        details: Array<String>,
        t: Throwable?,
    ): Set<Action> {
        if (isUltimatePostStartupConstructorError(message, t)) {
            return Action.NONE
        }
        return super.processError(category, message, details, t)
    }
}

private val suppressedUltimatePostStartupErrors: AccessToken by lazy {
    LoggedErrorProcessor.executeWith(UltimatePostStartupLoggedErrorProcessor)
}

fun suppressUltimatePostStartupConstructorErrors() {
    suppressedUltimatePostStartupErrors
}

fun isUltimatePostStartupConstructorError(
    message: String?,
    throwable: Throwable?,
): Boolean {
    val combined =
        buildString {
            if (!message.isNullOrBlank()) {
                append(message)
            }
            var cause = throwable
            while (cause != null) {
                if (isNotEmpty()) {
                    append('\n')
                }
                append(cause.message.orEmpty())
                cause = cause.cause
            }
        }
    return "com.intellij.modules.ultimate" in combined &&
        "Cannot find suitable constructor" in combined
}
