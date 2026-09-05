package com.linecorp.intellij.plugins.armeria.debugger

import com.linecorp.intellij.plugins.armeria.message

/** Debugger renderer for Armeria [com.linecorp.armeria.client.ClientRequestContext]. */
class ArmeriaClientRequestContextRendererProvider : ArmeriaRequestContextRendererProvider() {
    override fun getName(): String = message("debugger.client.request.context.renderer.name")

    override fun getClassName(): String = CLIENT_REQUEST_CONTEXT_CLASS

    override val timeoutChildName: String = CHILD_RESPONSE_TIMEOUT

    override val timeoutExpression: String = "responseTimeoutMillis()"
}
