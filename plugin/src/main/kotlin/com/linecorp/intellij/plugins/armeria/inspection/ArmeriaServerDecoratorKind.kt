package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind

internal enum class ArmeriaServerDecoratorKind {
    AUTH,
    LOGGING,
    CORS,
    ;

    companion object {
        fun fromSimpleName(simpleName: String): ArmeriaServerDecoratorKind? =
            when (simpleName.removeSuffix("Builder")) {
                "AuthService" -> AUTH
                "LoggingService" -> LOGGING
                "CorsService" -> CORS
                else -> null
            }
    }
}

internal data class ArmeriaServerDecoratorFinding(
    val highlight: PsiElement,
    val messageKey: String,
)

internal object ArmeriaServerDecoratorMessages {
    const val SERVICE_WITH_ROUTES = "inspection.server.decorator.service.with.routes"
    const val GRPC_CORS = "inspection.server.decorator.grpc.cors"
    const val AUTH_AFTER_LOGGING = "inspection.server.decorator.auth.after.logging"
}

internal object ArmeriaServerDecoratorTypes {
    const val HTTP_SERVICE = "com.linecorp.armeria.server.HttpService"
    const val HTTP_SERVICE_WITH_ROUTES = "com.linecorp.armeria.server.HttpServiceWithRoutes"

    private val SERVICE_WITH_ROUTES_KINDS =
        setOf(
            KnownHttpServiceKind.GRPC,
            KnownHttpServiceKind.GRAPHQL,
            KnownHttpServiceKind.THRIFT,
            KnownHttpServiceKind.DOC_SERVICE,
            KnownHttpServiceKind.FILE,
        )

    fun isServiceWithRoutesKind(kind: KnownHttpServiceKind): Boolean = kind in SERVICE_WITH_ROUTES_KINDS

    fun corsPathAppliesToGrpc(pathPattern: String): Boolean {
        val normalized = pathPattern.trim()
        return normalized.isEmpty() ||
            normalized == "/" ||
            normalized == "/**" ||
            normalized == "*" ||
            normalized == "prefix:/" ||
            normalized == "prefix:/**" ||
            normalized == "glob:/**" ||
            normalized == "glob:/*"
    }
}
