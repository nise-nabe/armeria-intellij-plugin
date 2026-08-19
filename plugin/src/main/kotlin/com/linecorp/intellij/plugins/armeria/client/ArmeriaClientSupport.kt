package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.message

internal enum class ClientProtocol(
    private val messageKey: String,
) {
    HTTP("route.explorer.protocol.http"),
    GRPC("route.explorer.protocol.grpc"),
    THRIFT("route.explorer.protocol.thrift"),
    RETROFIT("client.explorer.protocol.retrofit"),
    REST("client.explorer.protocol.rest"),
    BLOCKING("client.explorer.protocol.blocking"),
    ;

    fun presentableName(): String = message(messageKey)

    fun matchesRouteProtocol(routeProtocol: String): Boolean =
        when (this) {
            HTTP, RETROFIT, REST, BLOCKING -> routeProtocol == HTTP.presentableName()
            GRPC -> routeProtocol == GRPC.presentableName()
            THRIFT -> routeProtocol == THRIFT.presentableName()
        }

    companion object {
        fun fromPresentableName(name: String): ClientProtocol? = entries.firstOrNull { it.presentableName() == name }
    }
}

internal object ArmeriaClientSupport {
    const val ARMERIA_CLIENT_PACKAGE_PREFIX = "com.linecorp.armeria.client"
    const val WEB_CLIENT_CLASS = "com.linecorp.armeria.client.WebClient"

    private val CLIENT_CLASS_PROTOCOLS =
        mapOf(
            WEB_CLIENT_CLASS to ClientProtocol.HTTP,
            "com.linecorp.armeria.client.RestClient" to ClientProtocol.REST,
            "com.linecorp.armeria.client.BlockingWebClient" to ClientProtocol.BLOCKING,
            "com.linecorp.armeria.client.blocking.BlockingWebClient" to ClientProtocol.BLOCKING,
            "com.linecorp.armeria.client.grpc.GrpcClient" to ClientProtocol.GRPC,
            "com.linecorp.armeria.client.grpc.GrpcClients" to ClientProtocol.GRPC,
            "com.linecorp.armeria.client.thrift.ThriftClient" to ClientProtocol.THRIFT,
            "com.linecorp.armeria.client.thrift.ThriftClients" to ClientProtocol.THRIFT,
            "com.linecorp.armeria.client.retrofit2.ArmeriaRetrofit" to ClientProtocol.RETROFIT,
        )

    private val CLIENT_SIMPLE_NAME_PROTOCOLS =
        CLIENT_CLASS_PROTOCOLS.mapKeys { (fqcn, _) -> fqcn.substringAfterLast('.') }

    val FACTORY_METHOD_NAMES = setOf("builder", "of", "newClient")

    val CONVERSION_METHOD_NAMES = setOf("blocking", "asRestClient")

    val HTTP_METHOD_INVOCATION_NAMES = setOf("get", "post", "put", "delete", "patch")

    val HTTP_INVOCATION_METHOD_NAMES = HTTP_METHOD_INVOCATION_NAMES + "execute"

    private val HTTP_CLIENT_PROTOCOLS =
        setOf(ClientProtocol.HTTP, ClientProtocol.REST, ClientProtocol.BLOCKING)

    private val WEB_CLIENT_CLASS_NAMES =
        setOf(
            WEB_CLIENT_CLASS,
            "WebClient",
        )

    fun protocolForClass(qualifiedName: String?): ClientProtocol? = qualifiedName?.let { CLIENT_CLASS_PROTOCOLS[it] }

    fun protocolForSimpleName(simpleName: String): ClientProtocol? = CLIENT_SIMPLE_NAME_PROTOCOLS[simpleName]

    fun protocolForInvocation(
        methodName: String,
        containingClass: String?,
    ): ClientProtocol? {
        if (methodName in CONVERSION_METHOD_NAMES) {
            return if (isWebClientClass(containingClass)) protocolForConversion(methodName) else null
        }
        if (methodName !in FACTORY_METHOD_NAMES) {
            return null
        }
        return protocolForClass(containingClass)
    }

    fun protocolForConversion(methodName: String): ClientProtocol? =
        when (methodName) {
            "blocking" -> ClientProtocol.BLOCKING
            "asRestClient" -> ClientProtocol.REST
            else -> null
        }

    /** Simple class names used by text-based Scala client scanning. */
    fun clientSimpleNames(): Set<String> = CLIENT_SIMPLE_NAME_PROTOCOLS.keys

    fun isWebClientClass(qualifiedName: String?): Boolean =
        qualifiedName != null &&
            (qualifiedName in WEB_CLIENT_CLASS_NAMES || qualifiedName.endsWith(".WebClient"))

    fun isHttpClientClass(qualifiedName: String?): Boolean {
        val protocol = protocolForClass(qualifiedName) ?: return false
        return protocol in HTTP_CLIENT_PROTOCOLS
    }

    fun httpMethodForInvocation(methodName: String): String? =
        when (methodName) {
            "get", "GET" -> "GET"
            "post", "POST" -> "POST"
            "put", "PUT" -> "PUT"
            "delete", "DELETE" -> "DELETE"
            "patch", "PATCH" -> "PATCH"
            else -> null
        }

    fun wrapsWebClientTransport(protocol: ClientProtocol): Boolean = protocol == ClientProtocol.RETROFIT || protocol == ClientProtocol.REST

    fun looksLikeClientBuilderReceiverText(text: String): Boolean {
        val simpleName = text.substringAfterLast('.')
        return simpleName.endsWith("ClientBuilder") ||
            simpleName == "ArmeriaRetrofitBuilder" ||
            simpleName.endsWith("Builder") &&
            text.contains("armeria")
    }
}
