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
    SAML("route.explorer.protocol.saml"),
    ZOOKEEPER("route.explorer.protocol.zookeeper"),
    EUREKA("route.explorer.protocol.eureka"),
    CONSUL("route.explorer.protocol.consul"),
    NACOS("route.explorer.protocol.nacos"),
    ;

    fun presentableName(): String = message(messageKey)
}
