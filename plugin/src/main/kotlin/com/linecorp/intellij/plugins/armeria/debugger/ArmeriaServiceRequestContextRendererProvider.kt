package com.linecorp.intellij.plugins.armeria.debugger

import com.linecorp.intellij.plugins.armeria.message

/** Debugger renderer for Armeria [com.linecorp.armeria.server.ServiceRequestContext]. */
class ArmeriaServiceRequestContextRendererProvider : ArmeriaRequestContextRendererProvider() {
    override fun getName(): String = message("debugger.service.request.context.renderer.name")

    override fun getClassName(): String = SERVICE_REQUEST_CONTEXT_CLASS

    override val timeoutChildName: String = CHILD_REQUEST_TIMEOUT

    override val timeoutExpression: String = "requestTimeoutMillis()"
}
