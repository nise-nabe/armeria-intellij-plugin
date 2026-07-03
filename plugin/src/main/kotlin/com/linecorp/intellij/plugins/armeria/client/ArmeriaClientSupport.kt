package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.message

internal enum class ClientProtocol(private val messageKey: String) {
    HTTP("route.explorer.protocol.http"),
    GRPC("route.explorer.protocol.grpc"),
    THRIFT("route.explorer.protocol.thrift"),
    ;

    fun presentableName(): String = message(messageKey)
}

internal object ArmeriaClientSupport {
    const val ARMERIA_CLIENT_PACKAGE_PREFIX = "com.linecorp.armeria.client"

    private val CLIENT_CLASS_PROTOCOLS = mapOf(
        "com.linecorp.armeria.client.WebClient" to ClientProtocol.HTTP,
        "com.linecorp.armeria.client.grpc.GrpcClient" to ClientProtocol.GRPC,
        "com.linecorp.armeria.client.grpc.GrpcClients" to ClientProtocol.GRPC,
        "com.linecorp.armeria.client.thrift.ThriftClient" to ClientProtocol.THRIFT,
        "com.linecorp.armeria.client.thrift.ThriftClients" to ClientProtocol.THRIFT,
    )

    val FACTORY_METHOD_NAMES = setOf("builder", "of", "newClient")

    fun protocolForClass(qualifiedName: String?): ClientProtocol? =
        qualifiedName?.let { CLIENT_CLASS_PROTOCOLS[it] }
}
