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
    HTTP,
}

/**
 * Maps resolved Armeria `HttpService` type names to Route Explorer protocol / match.
 *
 * Classification is by identifier (FQCN or simple name), not raw PSI `.text`.
 */
object ArmeriaKnownHttpServiceClassifier {
    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val KIND_BY_SIMPLE_NAME =
        mapOf(
            "DocService" to KnownHttpServiceKind.DOC_SERVICE,
            "GrpcService" to KnownHttpServiceKind.GRPC,
            "GraphqlService" to KnownHttpServiceKind.GRAPHQL,
            "THttpService" to KnownHttpServiceKind.THRIFT,
            "PrometheusExpositionService" to KnownHttpServiceKind.METRICS,
            "FileService" to KnownHttpServiceKind.FILE,
        )

    fun classify(typeName: String): KnownHttpServiceKind {
        kindForSimpleName(simpleName(typeName))?.let { return it }
        IDENTIFIER.findAll(typeName).forEach { match ->
            kindForSimpleName(canonicalSimpleName(match.value))?.let { return it }
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

    fun protocol(kind: KnownHttpServiceKind): RouteProtocol =
        when (kind) {
            KnownHttpServiceKind.DOC_SERVICE -> RouteProtocol.DOC_SERVICE
            KnownHttpServiceKind.GRPC -> RouteProtocol.GRPC
            KnownHttpServiceKind.GRAPHQL -> RouteProtocol.GRAPHQL
            KnownHttpServiceKind.THRIFT -> RouteProtocol.THRIFT
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
        return when (registrationMethod) {
            CoreServiceRegistrationMethod.SERVICE -> RouteMatch.SERVICE
            CoreServiceRegistrationMethod.ANNOTATED_SERVICE -> RouteMatch.ANNOTATED_SERVICE
            CoreServiceRegistrationMethod.SERVICE_UNDER -> RouteMatch.SERVICE_UNDER
        }
    }

    fun isDocService(kind: KnownHttpServiceKind): Boolean = kind == KnownHttpServiceKind.DOC_SERVICE

    fun excludeFromDuplicateIndex(kind: KnownHttpServiceKind): Boolean =
        kind == KnownHttpServiceKind.METRICS ||
            kind == KnownHttpServiceKind.DOC_SERVICE ||
            kind == KnownHttpServiceKind.FILE

    fun excludeFromDuplicateIndex(target: String): Boolean = excludeFromDuplicateIndex(classify(target))

    fun canonicalServiceTypeName(qualifiedOrSimpleName: String): String {
        val trimmed = qualifiedOrSimpleName.substringBefore('(').trim().trimEnd('?')
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

    private fun simpleName(typeName: String): String = canonicalSimpleName(canonicalServiceTypeName(typeName).substringAfterLast('.'))

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
