package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.FluentRouteChainInfo
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.RouteValueResolution
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteTargetExtractor

/**
 * Resolves a `Route` value passed to `ServerBuilder.service(Route, HttpService)`.
 *
 * Armeria exposes that binding as `service(Route, HttpService)` and builds the
 * `Route` with `Route.builder()…build()` (`RouteBuilder.build()` takes no handler).
 * `Route.of` is not part of the documented Route API; it is recognized only when
 * the receiver is `com.linecorp.armeria.server.Route`.
 *
 * An expression whose path cannot be resolved produces no route.
 */
internal object ArmeriaJavaRouteValueExtractor {
    private const val ARMERIA_ROUTE_CLASS = "com.linecorp.armeria.server.Route"
    private const val MAX_ROUTE_VALUE_HOPS = 8
    private val ROUTE_BUILD_OWNERS =
        setOf(
            "com.linecorp.armeria.server.RouteBuilder",
            "com.linecorp.armeria.server.ServiceBindingBuilder",
            "com.linecorp.armeria.server.VirtualHostServiceBindingBuilder",
        )
    private val HTTP_METHODS =
        setOf("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "TRACE", "CONNECT")

    fun isArmeriaRouteBuilderFactory(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "builder" || call.argumentList.expressionCount != 0) {
            return false
        }
        val qualifier = call.methodExpression.qualifierExpression ?: return false
        return isArmeriaRouteReference(qualifier)
    }

    fun isServiceRouteArgument(buildCall: PsiMethodCallExpression): Boolean {
        var expression: PsiExpression = buildCall
        var parent = expression.parent
        while (parent is PsiParenthesizedExpression || parent is PsiTypeCastExpression) {
            expression = parent
            parent = parent.parent
        }
        val argumentList = parent as? PsiExpressionList ?: return false
        val owner = argumentList.parent as? PsiMethodCallExpression ?: return false
        if (owner.methodExpression.referenceName != "service") {
            return false
        }
        val routeArgument = argumentList.expressions.firstOrNull() ?: return false
        val unwrapped = ArmeriaRouteTargetExtractor.unwrapCast(routeArgument) ?: return false
        return unwrapped == buildCall || PsiTreeUtil.isAncestor(unwrapped, buildCall, false)
    }

    /**
     * True when `build()` belongs to [RouteBuilder] or a service-binding builder.
     * A constant `path` / `post` / `get` step on that receiver is enough to emit a
     * route; `ServerBuilder.build()` stays excluded.
     */
    fun isArmeriaRouteValueBuild(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "build") {
            return false
        }
        val owner = resolveMethod(call)?.containingClass?.qualifiedName
        return owner in ROUTE_BUILD_OWNERS
    }

    fun extractResolvedRoute(
        expression: PsiExpression,
        target: String,
    ): FluentRouteChainInfo? = extract(expression, target, RouteValueResolution(), 0)

    internal fun extract(
        expression: PsiExpression?,
        target: String,
        resolution: RouteValueResolution,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (expression == null || hops >= MAX_ROUTE_VALUE_HOPS) {
            return null
        }
        val unwrapped = ArmeriaRouteTargetExtractor.unwrapCast(expression) ?: return null
        return when (unwrapped) {
            is PsiMethodCallExpression -> extractFromCall(unwrapped, target, resolution, hops)
            is PsiReferenceExpression -> extractFromReference(unwrapped, target, resolution, hops)
            else -> null
        }
    }

    private fun extractFromCall(
        call: PsiMethodCallExpression,
        target: String,
        resolution: RouteValueResolution,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (isArmeriaRouteOfCall(call)) {
            return extractRouteOf(call, target)
        }
        if (call.methodExpression.referenceName == "build" &&
            ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)
        ) {
            return ArmeriaExtendedRegistrationCollectorFluentRoute.extractFluentRouteChain(
                call,
                requireRouteAnchor = true,
                handlerTarget = target,
            )
        }
        if (isArmeriaRouteBuilderFactory(call)) {
            return null
        }
        val method = resolveMethod(call) ?: return null
        return extractFromMethod(method, target, resolution, hops)
    }

    internal fun extractFromMethod(
        method: PsiMethod,
        target: String,
        resolution: RouteValueResolution,
        hops: Int,
    ): FluentRouteChainInfo? {
        resolution.cached(method, target)?.let { return it }
        if (isArmeriaRouteOfCatchAll(method)) {
            val catchAll = catchAllRoute(target)
            resolution.exit(method, catchAll)
            return catchAll
        }
        if (!resolution.enter(method, hops, MAX_ROUTE_VALUE_HOPS)) {
            return null
        }
        val body = method.body
        val result =
            if (body == null) {
                null
            } else {
                val returns =
                    PsiTreeUtil
                        .findChildrenOfType(body, PsiReturnStatement::class.java)
                        .filter { statement ->
                            PsiTreeUtil.getParentOfType(statement, PsiMethod::class.java) == method &&
                                PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression::class.java) == null &&
                                PsiTreeUtil.getParentOfType(statement, PsiAnonymousClass::class.java) == null
                        }
                singleResolvedRoute(
                    returns.map { statement ->
                        extract(statement.returnValue, target, resolution, hops + 1)
                    },
                )
            }
        resolution.exit(method, result)
        return result
    }

    private fun extractFromReference(
        reference: PsiReferenceExpression,
        target: String,
        resolution: RouteValueResolution,
        hops: Int,
    ): FluentRouteChainInfo? {
        val resolved = resolve(reference) ?: return null
        if (resolved !is PsiVariable) {
            return null
        }
        resolution.cached(resolved, target)?.let { return it }
        if (!resolution.enter(resolved, hops, MAX_ROUTE_VALUE_HOPS)) {
            return null
        }
        val result = extract(resolved.initializer, target, resolution, hops + 1)
        resolution.exit(resolved, result)
        return result
    }

    private fun isArmeriaRouteOfCatchAll(method: PsiMethod): Boolean =
        method.name == "ofCatchAll" && method.containingClass?.qualifiedName == ARMERIA_ROUTE_CLASS

    private fun catchAllRoute(target: String): FluentRouteChainInfo =
        FluentRouteChainInfo(
            httpMethod = "",
            path = "/*",
            pathType = PathType.PREFIX,
            target = target,
        )

    private fun extractRouteOf(
        call: PsiMethodCallExpression,
        target: String,
    ): FluentRouteChainInfo? {
        val arguments = call.argumentList.expressions
        val pathExpression: PsiExpression
        val httpMethod: String
        when (arguments.size) {
            1 -> {
                httpMethod = ""
                pathExpression = arguments[0]
            }
            2 -> {
                httpMethod = httpMethodLabel(arguments[0]) ?: return null
                pathExpression = arguments[1]
            }
            else -> return null
        }
        val rawPath = ArmeriaJavaRegistrationChainSupport.extractString(pathExpression) ?: return null
        val (pathType, path) = ArmeriaRouteSupport.parsePathType(rawPath)
        return FluentRouteChainInfo(
            httpMethod = httpMethod,
            path = path,
            pathType = pathType,
            target = target,
        )
    }

    private fun isArmeriaRouteOfCall(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "of") {
            return false
        }
        val qualifier = call.methodExpression.qualifierExpression ?: return false
        return isArmeriaRouteReference(qualifier)
    }

    private fun isArmeriaRouteReference(expression: PsiExpression): Boolean {
        val reference = expression as? PsiReferenceExpression ?: return false
        val resolved = resolve(reference)
        return when (resolved) {
            is PsiClass -> resolved.qualifiedName == ARMERIA_ROUTE_CLASS
            null -> reference.text == ARMERIA_ROUTE_CLASS
            else -> false
        }
    }

    private fun httpMethodLabel(expression: PsiExpression): String? {
        if (ArmeriaJavaRegistrationChainSupport.extractString(expression) != null) {
            return null
        }
        val simple =
            expression.text
                .trim()
                .substringAfterLast('.')
                .removeSuffix("()")
                .uppercase()
        return simple.takeIf { it in HTTP_METHODS }
    }

    private fun singleResolvedRoute(resolved: List<FluentRouteChainInfo?>): FluentRouteChainInfo? {
        if (resolved.isEmpty() || resolved.any { it == null }) {
            return null
        }
        return resolved.filterNotNull().distinctBy { Triple(it.httpMethod, it.path, it.pathType) }.singleOrNull()
    }

    private fun resolveMethod(call: PsiMethodCallExpression): PsiMethod? {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        return call.resolveMethod()
    }

    private fun resolve(reference: PsiReferenceExpression): PsiElement? {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        return reference.resolve()
    }
}
