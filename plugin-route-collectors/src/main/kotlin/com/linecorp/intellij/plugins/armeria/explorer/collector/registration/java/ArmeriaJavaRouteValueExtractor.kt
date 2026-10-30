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

    fun extractResolvedRoute(
        expression: PsiExpression,
        target: String,
    ): FluentRouteChainInfo? = extract(expression, target, mutableSetOf(), 0)

    internal fun extract(
        expression: PsiExpression?,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (expression == null || hops >= MAX_ROUTE_VALUE_HOPS) {
            return null
        }
        val unwrapped = ArmeriaRouteTargetExtractor.unwrapCast(expression) ?: return null
        return when (unwrapped) {
            is PsiMethodCallExpression -> extractFromCall(unwrapped, target, visited, hops)
            is PsiReferenceExpression -> extractFromReference(unwrapped, target, visited, hops)
            else -> null
        }
    }

    private fun extractFromCall(
        call: PsiMethodCallExpression,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (isArmeriaRouteOfCall(call)) {
            return extractRouteOf(call, target)
        }
        if (call.methodExpression.referenceName == "build") {
            if (!ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)) {
                return null
            }
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
        return extractFromMethod(method, target, visited, hops)
    }

    internal fun extractFromMethod(
        method: PsiMethod,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (hops >= MAX_ROUTE_VALUE_HOPS || !visited.add(method)) {
            return null
        }
        val body = method.body ?: return null
        val returns =
            PsiTreeUtil
                .findChildrenOfType(body, PsiReturnStatement::class.java)
                .filter { statement ->
                    PsiTreeUtil.getParentOfType(statement, PsiMethod::class.java) == method &&
                        PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression::class.java) == null &&
                        PsiTreeUtil.getParentOfType(statement, PsiAnonymousClass::class.java) == null
                }
        return singleResolvedRoute(
            returns.map { statement ->
                extract(statement.returnValue, target, visited, hops + 1)
            },
        )
    }

    private fun extractFromReference(
        reference: PsiReferenceExpression,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        val resolved = resolve(reference) ?: return null
        if (resolved !is PsiVariable || !visited.add(resolved)) {
            return null
        }
        return extract(resolved.initializer, target, visited, hops + 1)
    }

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
