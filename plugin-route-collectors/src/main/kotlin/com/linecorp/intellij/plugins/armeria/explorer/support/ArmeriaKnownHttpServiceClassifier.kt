package com.linecorp.intellij.plugins.armeria.explorer.support

import com.linecorp.intellij.plugins.armeria.explorer.model.CoreServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol

enum class KnownHttpServiceKind {
    DOC_SERVICE,
    GRPC,
    GRAPHQL,
    THRIFT,
    METRICS,
    FILE,
    WEBSOCKET,
    SSE,
    HEALTH_CHECK,
    HTTP,
}

/**
 * Maps resolved Armeria `HttpService` type names to Route Explorer protocol / match.
 *
 * Classification is by identifier (FQCN or simple name), not raw PSI `.text`.
 * User types outside `com.linecorp.armeria` are not classified by simple name
 * (`example.FileService` stays HTTP). Call-chain text (`DocService.builder().build()`)
 * is classified by identifier tokens for Scala registrations.
 */
object ArmeriaKnownHttpServiceClassifier {
    private const val ARMERIA_PACKAGE_PREFIX = "com.linecorp.armeria"
    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val KIND_BY_SIMPLE_NAME =
        mapOf(
            "DocService" to KnownHttpServiceKind.DOC_SERVICE,
            "GrpcService" to KnownHttpServiceKind.GRPC,
            "GraphqlService" to KnownHttpServiceKind.GRAPHQL,
            "THttpService" to KnownHttpServiceKind.THRIFT,
            "PrometheusExpositionService" to KnownHttpServiceKind.METRICS,
            "FileService" to KnownHttpServiceKind.FILE,
            "WebSocketService" to KnownHttpServiceKind.WEBSOCKET,
            "ServerSentEvents" to KnownHttpServiceKind.SSE,
            "HealthCheckService" to KnownHttpServiceKind.HEALTH_CHECK,
        )

    fun classify(typeName: String): KnownHttpServiceKind {
        val className =
            typeName
                .trim()
                .removePrefix("new ")
                .trim()
                .substringBefore('#')
                .substringBefore('(')
                .trim()
        val packagePrefix = className.substringBeforeLast('.', missingDelimiterValue = "")
        if (packagePrefix.startsWith(ARMERIA_PACKAGE_PREFIX)) {
            kindForSimpleName(canonicalSimpleName(className.substringAfterLast('.')))
                ?.let { return it }
            return if ('(' in typeName && '#' !in typeName) {
                classifyByIdentifierTokens(typeName)
            } else {
                KnownHttpServiceKind.HTTP
            }
        }
        if (packagePrefix.isNotEmpty()) {
            return if (isUnqualifiedCallChain(typeName, className)) {
                classifyByIdentifierTokens(typeName)
            } else {
                KnownHttpServiceKind.HTTP
            }
        }
        kindForSimpleName(canonicalSimpleName(className))?.let { return it }
        if (isUnqualifiedCallChain(typeName, className)) {
            return classifyByIdentifierTokens(typeName)
        }
        return KnownHttpServiceKind.HTTP
    }

    fun classify(vararg typeNames: String): KnownHttpServiceKind {
        for (typeName in typeNames) {
            if (typeName.isBlank()) {
                continue
            }
            val kind = classify(typeName)
            if (kind != KnownHttpServiceKind.HTTP) {
                return kind
            }
        }
        return KnownHttpServiceKind.HTTP
    }

    fun isKnownServiceType(typeName: String): Boolean = classify(typeName) != KnownHttpServiceKind.HTTP

    fun knownServiceTypeNameOrNull(typeName: String?): String? = typeName?.takeIf(::isKnownServiceType)

    fun protocol(kind: KnownHttpServiceKind): RouteProtocol =
        when (kind) {
            KnownHttpServiceKind.DOC_SERVICE -> RouteProtocol.DOC_SERVICE
            KnownHttpServiceKind.GRPC -> RouteProtocol.GRPC
            KnownHttpServiceKind.GRAPHQL -> RouteProtocol.GRAPHQL
            KnownHttpServiceKind.THRIFT -> RouteProtocol.THRIFT
            KnownHttpServiceKind.WEBSOCKET -> RouteProtocol.WEBSOCKET
            KnownHttpServiceKind.SSE -> RouteProtocol.SSE
            KnownHttpServiceKind.HEALTH_CHECK -> RouteProtocol.HEALTH_CHECK
            KnownHttpServiceKind.METRICS,
            KnownHttpServiceKind.FILE,
            KnownHttpServiceKind.HTTP,
            -> RouteProtocol.HTTP
        }

    fun routeMatch(
        kind: KnownHttpServiceKind,
        registrationMethod: CoreServiceRegistrationMethod,
    ): RouteMatch {
        if (isNonHttp(kind)) {
            return RouteMatch.NON_HTTP
        }
        if (kind == KnownHttpServiceKind.FILE &&
            registrationMethod != CoreServiceRegistrationMethod.ANNOTATED_SERVICE
        ) {
            return RouteMatch.FILE_SERVICE
        }
        if (kind == KnownHttpServiceKind.HEALTH_CHECK &&
            registrationMethod != CoreServiceRegistrationMethod.ANNOTATED_SERVICE
        ) {
            return RouteMatch.HEALTH_CHECK
        }
        return when (registrationMethod) {
            CoreServiceRegistrationMethod.SERVICE -> RouteMatch.SERVICE
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE -> RouteMatch.ANNOTATED_SERVICE
            CoreServiceRegistrationMethod.SERVICE_UNDER -> RouteMatch.SERVICE_UNDER
        }
    }

    fun defaultHttpMethod(kind: KnownHttpServiceKind): String =
        when (kind) {
            KnownHttpServiceKind.HEALTH_CHECK, KnownHttpServiceKind.SSE -> "GET"
            else -> ""
        }

    fun isDocService(kind: KnownHttpServiceKind): Boolean = kind == KnownHttpServiceKind.DOC_SERVICE

    fun excludeFromDuplicateIndex(kind: KnownHttpServiceKind): Boolean =
        kind == KnownHttpServiceKind.METRICS ||
            kind == KnownHttpServiceKind.DOC_SERVICE ||
            kind == KnownHttpServiceKind.FILE

    fun excludeFromDuplicateIndex(target: String): Boolean = excludeFromDuplicateIndex(classify(target))

    fun canonicalServiceTypeName(qualifiedOrSimpleName: String): String {
        val trimmed =
            qualifiedOrSimpleName
                .substringBefore('#')
                .substringBefore('(')
                .trim()
                .trimEnd('?')
        val simpleName = trimmed.substringAfterLast('.')
        if (!simpleName.endsWith("Builder")) {
            return trimmed
        }
        val serviceSimpleName = simpleName.removeSuffix("Builder")
        val packagePrefix = trimmed.substringBeforeLast('.', missingDelimiterValue = "")
        return if (packagePrefix.isEmpty()) {
            serviceSimpleName
        } else {
            "$packagePrefix.$serviceSimpleName"
        }
    }

    /**
     * Scala registrations pass PSI call-chain text (`DocService.builder().build()`).
     * Annotated targets (`example.FileService#get()`) and user FQCNs must not be
     * token-scanned: `#method()` contains parentheses, and `example.FileService.of()`
     * contains a known simple name after a non-Armeria package.
     */
    private fun isUnqualifiedCallChain(
        typeName: String,
        className: String,
    ): Boolean {
        if ('(' !in typeName || '#' in typeName) {
            return false
        }
        val firstSegment = className.substringBefore('.')
        return firstSegment.firstOrNull()?.isUpperCase() == true
    }

    private fun classifyByIdentifierTokens(typeName: String): KnownHttpServiceKind {
        IDENTIFIER.findAll(typeName).forEach { match ->
            kindForSimpleName(canonicalSimpleName(match.value))?.let { return it }
        }
        return KnownHttpServiceKind.HTTP
    }

    private fun canonicalSimpleName(simpleName: String): String =
        if (simpleName.endsWith("Builder")) {
            simpleName.removeSuffix("Builder")
        } else {
            simpleName
        }

    private fun kindForSimpleName(simpleName: String): KnownHttpServiceKind? = KIND_BY_SIMPLE_NAME[simpleName]

    private fun isNonHttp(kind: KnownHttpServiceKind): Boolean =
        kind == KnownHttpServiceKind.DOC_SERVICE ||
            kind == KnownHttpServiceKind.GRPC ||
            kind == KnownHttpServiceKind.GRAPHQL ||
            kind == KnownHttpServiceKind.THRIFT
}
