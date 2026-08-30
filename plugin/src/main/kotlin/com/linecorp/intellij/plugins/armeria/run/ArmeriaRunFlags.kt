package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.configurations.JavaParameters

object ArmeriaRunFlags {
    const val VERBOSE_RESPONSES = "com.linecorp.armeria.verboseResponses"
    const val REPORT_BLOCKED_EVENT_LOOP = "com.linecorp.armeria.reportBlockedEventLoop"

    fun systemProperties(
        verboseResponses: Boolean,
        reportBlockedEventLoop: Boolean,
    ): List<String> =
        buildList {
            if (verboseResponses) {
                add("-D$VERBOSE_RESPONSES=true")
            }
            if (reportBlockedEventLoop) {
                add("-D$REPORT_BLOCKED_EVENT_LOOP=true")
            }
        }

    fun apply(
        parameters: JavaParameters,
        verboseResponses: Boolean,
        reportBlockedEventLoop: Boolean,
    ) {
        for (property in systemProperties(verboseResponses, reportBlockedEventLoop)) {
            parameters.vmParametersList.add(property)
        }
    }
}
