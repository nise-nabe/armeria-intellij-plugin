package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.AccessToken
import com.intellij.testFramework.LoggedErrorProcessor

/**
 * IDEA Ultimate 2026.2.2 re-obfuscates `com.intellij.modules.ultimate`'s
 * [com.intellij.openapi.startup.ProjectActivity] to `B.B.B.B.s`. That FQCN
 * collides with `interface B.B.B.B.s` in boot-classpath `product-backend.jar`.
 * Fixture tests load both JARs through the same [com.intellij.util.lang.PathClassLoader],
 * so the platform instantiates the interface (no constructors) on project open and
 * [com.intellij.testFramework.TestLoggerFactory] fails the test.
 *
 * 2026.2 used `k.k.k.k.a`, which did not collide; the same package-private no-arg
 * constructor is accepted there. This is not an Armeria API-usage bug.
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
