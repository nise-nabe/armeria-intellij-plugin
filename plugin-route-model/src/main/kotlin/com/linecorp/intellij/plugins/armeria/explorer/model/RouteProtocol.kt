package com.linecorp.intellij.plugins.armeria.explorer.model
import com.linecorp.intellij.plugins.armeria.message

enum class RouteProtocol(
    private val messageKey: String,
) {
    HTTP("route.explorer.protocol.http"),
    GRPC("route.explorer.protocol.grpc"),
    DOC_SERVICE("route.explorer.protocol.docService"),
    GRAPHQL("route.explorer.protocol.graphql"),
    THRIFT("route.explorer.protocol.thrift"),
    WEBSOCKET("route.explorer.protocol.websocket"),
    SSE("route.explorer.protocol.sse"),
    HEALTH_CHECK("route.explorer.protocol.healthCheck"),
    ;

    fun presentableName(): String = message(messageKey)
}
