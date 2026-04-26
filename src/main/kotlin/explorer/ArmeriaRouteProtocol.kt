package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.message

internal enum class ArmeriaRouteProtocol(private val messageKey: String) {
    HTTP("route.explorer.protocol.http"),
    GRPC("route.explorer.protocol.grpc"),
    DOC_SERVICE("route.explorer.protocol.docService"),
    THRIFT("route.explorer.protocol.thrift"),
    ;

    fun presentableName(): String = message(messageKey)
}
