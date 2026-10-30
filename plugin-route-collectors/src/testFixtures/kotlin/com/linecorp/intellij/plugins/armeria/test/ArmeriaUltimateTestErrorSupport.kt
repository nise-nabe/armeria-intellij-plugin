package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.AccessToken
import com.intellij.testFramework.LoggedErrorProcessor

/**
 * IDEA Ultimate 2026.2 re-obfuscates `com.intellij.modules.ultimate`'s
 * [com.intellij.openapi.startup.ProjectActivity] to a short FQCN that collides with
 * an interface of the same name in boot-classpath `product-backend.jar`. Fixture tests
 * load both JARs through the same [com.intellij.util.lang.PathClassLoader], so the
 * platform instantiates the interface (no constructors) on project open and
 * [com.intellij.testFramework.TestLoggerFactory] fails the test.
 *
 * On 2026.2.2 the colliding name was `B.B.B.B.s`. On 2026.2.4 (IU 262.11879.100) it is
 * `Q.Q.Q.Q.V`, reported as `Cannot create extension (class=Q.Q.Q.Q.V)` with cause
 * `Cannot find suitable constructor`. 2026.2.0 used `k.k.k.k.a`, which did not collide.
 * This is not an Armeria API-usage bug. Disabling this suppressor still fails
 * `ArmeriaPluginDisposableTest.testApplicationServiceIsRegistered` on 2026.2.4.
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
