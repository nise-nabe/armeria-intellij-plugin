package com.linecorp.intellij.plugins.armeria.debugger

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.debugger.ui.tree.render.ChildrenRenderer
import com.intellij.debugger.ui.tree.render.CompoundRendererProvider
import com.intellij.debugger.ui.tree.render.LabelRenderer
import com.intellij.debugger.ui.tree.render.ValueLabelRenderer

/**
 * Shared debugger-renderer helpers for Armeria request-context types.
 *
 * Class names are string FQCNs only — Armeria types are never loaded into the IDE process.
 * Label and child expressions are evaluated in the debuggee JVM when those types exist there.
 */
abstract class ArmeriaRequestContextRendererProvider : CompoundRendererProvider() {
    protected abstract val timeoutChildName: String
    protected abstract val timeoutExpression: String

    final override fun getValueLabelRenderer(): ValueLabelRenderer =
        LabelRenderer().apply {
            setLabelExpression(
                TextWithImportsImpl(
                    CodeFragmentKind.EXPRESSION,
                    LABEL_EXPRESSION,
                ),
            )
        }

    final override fun getChildrenRenderer(): ChildrenRenderer =
        NodeRendererSettings.createEnumerationChildrenRenderer(
            arrayOf(
                arrayOf(CHILD_METHOD, "method()"),
                arrayOf(CHILD_PATH, "path()"),
                arrayOf(CHILD_ID, "id()"),
                arrayOf(CHILD_REMOTE_ADDRESS, "remoteAddress()"),
                arrayOf(timeoutChildName, timeoutExpression),
            ),
        ).apply {
            isAppendDefaultChildren = true
        }

    final override fun isEnabled(): Boolean = true

    companion object {
        const val LABEL_EXPRESSION: String =
            """method() + " " + path() + " [" + id() + "]""""

        const val SERVICE_REQUEST_CONTEXT_CLASS: String =
            "com.linecorp.armeria.server.ServiceRequestContext"

        const val CLIENT_REQUEST_CONTEXT_CLASS: String =
            "com.linecorp.armeria.client.ClientRequestContext"

        const val CHILD_METHOD: String = "method"
        const val CHILD_PATH: String = "path"
        const val CHILD_ID: String = "id"
        const val CHILD_REMOTE_ADDRESS: String = "remoteAddress"
        const val CHILD_REQUEST_TIMEOUT: String = "requestTimeoutMillis"
        const val CHILD_RESPONSE_TIMEOUT: String = "responseTimeoutMillis"
    }
}
