package com.linecorp.intellij.plugins.armeria.explorer.model
import com.linecorp.intellij.plugins.armeria.message

internal enum class RouteProtocol(
    private val messageKey: String,
) {
    HTTP("route.explorer.protocol.http"),
    GRPC("route.explorer.protocol.grpc"),
    DOC_SERVICE("route.explorer.protocol.docService"),
    GRAPHQL("route.explorer.protocol.graphql"),
    THRIFT("route.explorer.protocol.thrift"),
    ;

    fun presentableName(): String = message(messageKey)
}
