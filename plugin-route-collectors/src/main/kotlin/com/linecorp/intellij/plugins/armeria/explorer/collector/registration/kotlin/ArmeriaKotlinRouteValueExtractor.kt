package com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.FluentRouteChainInfo
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java.ArmeriaJavaRouteValueExtractor
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Kotlin counterpart of [ArmeriaJavaRouteValueExtractor].
 * Unresolved paths are omitted.
 */
internal object ArmeriaKotlinRouteValueExtractor {
    private const val ARMERIA_ROUTE_CLASS = "com.linecorp.armeria.server.Route"
    private const val MAX_ROUTE_VALUE_HOPS = 8
    private val HTTP_METHODS =
        setOf("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "TRACE", "CONNECT")

    fun isArmeriaRouteBuilderFactory(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) != "builder" || call.valueArguments.isNotEmpty()) {
            return false
        }
        val qualified = call.parent as? KtDotQualifiedExpression ?: return false
        if (qualified.selectorExpression != call) {
            return false
        }
        return isArmeriaRouteReference(qualified.receiverExpression)
    }

    fun isServiceRouteArgument(buildCall: KtCallExpression): Boolean {
        val argument = buildCall.getParentOfType<KtValueArgument>(true) ?: return false
        val owner = (argument.parent as? KtValueArgumentList)?.parent as? KtCallExpression ?: return false
        if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(owner) != "service") {
            return false
        }
        val routeArgument =
            ArmeriaKotlinExpressionSupport.findArgumentExpression(owner.valueArguments, "route", -1)
                ?: ArmeriaKotlinExpressionSupport.findArgumentExpression(owner.valueArguments, "path", 0)
                ?: return false
        return routeArgument == buildCall || PsiTreeUtil.isAncestor(routeArgument, buildCall, false)
    }

    fun extractResolvedRoute(
        expression: KtExpression,
        target: String,
    ): FluentRouteChainInfo? = extract(expression, target, mutableSetOf(), 0)

    private fun extract(
        expression: KtExpression?,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (expression == null || hops >= MAX_ROUTE_VALUE_HOPS) {
            return null
        }
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return null
        return when (unwrapped) {
            is KtCallExpression -> extractFromCall(unwrapped, target, visited, hops)
            is KtDotQualifiedExpression -> extractFromQualified(unwrapped, target, visited, hops)
            is KtNameReferenceExpression -> extractFromReference(unwrapped, target, visited, hops)
            else -> null
        }
    }

    private fun extractFromQualified(
        expression: KtDotQualifiedExpression,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        val selector = expression.selectorExpression
        return when (selector) {
            is KtCallExpression -> extractFromCall(selector, target, visited, hops)
            is KtNameReferenceExpression -> extractFromReference(selector, target, visited, hops)
            else -> null
        }
    }

    private fun extractFromCall(
        call: KtCallExpression,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (isArmeriaRouteOfCall(call)) {
            return extractRouteOf(call, target)
        }
        val name = ArmeriaKotlinRegistrationChainSupport.resolveCallName(call)
        if (name == "build") {
            if (!ArmeriaBuilderCallHeuristics.looksLikeArmeriaFluentRouteBuild(call)) {
                return null
            }
            return ArmeriaKotlinExtendedRegistrationCollectorFluentRoute.extractFluentRouteChain(
                call,
                requireRouteAnchor = true,
                handlerTarget = target,
            )
        }
        if (isArmeriaRouteBuilderFactory(call)) {
            return null
        }
        return when (val resolved = resolveCallDeclaration(call)) {
            is KtNamedFunction -> extractFromKotlinFunction(resolved, target, visited, hops)
            is PsiMethod -> ArmeriaJavaRouteValueExtractor.extractFromMethod(resolved, target, visited, hops)
            else -> null
        }
    }

    private fun extractFromKotlinFunction(
        function: KtNamedFunction,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        if (hops >= MAX_ROUTE_VALUE_HOPS || !visited.add(function)) {
            return null
        }
        val body = function.bodyExpression ?: return null
        if (!function.hasBlockBody()) {
            return extract(body, target, visited, hops + 1)
        }
        val returns =
            PsiTreeUtil
                .findChildrenOfType(body, KtReturnExpression::class.java)
                .filter { returnExpression ->
                    returnExpression.getParentOfType<KtNamedFunction>(true) == function &&
                        returnExpression.getParentOfType<KtLambdaExpression>(true) == null
                }
        return singleResolvedRoute(
            returns.map { returnExpression ->
                extract(returnExpression.returnedExpression, target, visited, hops + 1)
            },
        )
    }

    private fun extractFromReference(
        reference: KtNameReferenceExpression,
        target: String,
        visited: MutableSet<PsiElement>,
        hops: Int,
    ): FluentRouteChainInfo? {
        val resolved = resolveReference(reference) ?: return null
        if (!visited.add(resolved)) {
            return null
        }
        return when (resolved) {
            is KtProperty -> extract(resolved.initializer, target, visited, hops + 1)
            is KtParameter -> null
            is PsiVariable -> {
                val initializer = resolved.initializer
                if (initializer is PsiExpression) {
                    ArmeriaJavaRouteValueExtractor.extract(initializer, target, visited, hops + 1)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun extractRouteOf(
        call: KtCallExpression,
        target: String,
    ): FluentRouteChainInfo? {
        val arguments = call.valueArguments.mapNotNull { it.getArgumentExpression() }
        val pathExpression: KtExpression
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
        val rawPath = ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(pathExpression) ?: return null
        val (pathType, path) = ArmeriaRouteSupport.parsePathType(rawPath)
        return FluentRouteChainInfo(
            httpMethod = httpMethod,
            path = path,
            pathType = pathType,
            target = target,
        )
    }

    private fun isArmeriaRouteOfCall(call: KtCallExpression): Boolean {
        if (ArmeriaKotlinRegistrationChainSupport.resolveCallName(call) != "of") {
            return false
        }
        val qualified = call.parent as? KtDotQualifiedExpression ?: return false
        if (qualified.selectorExpression != call) {
            return false
        }
        return isArmeriaRouteReference(qualified.receiverExpression)
    }

    private fun isArmeriaRouteReference(expression: KtExpression): Boolean {
        val unwrapped = ArmeriaKotlinExpressionSupport.unwrapKotlinExpression(expression) ?: return false
        val reference =
            when (unwrapped) {
                is KtNameReferenceExpression -> unwrapped
                is KtDotQualifiedExpression -> unwrapped.selectorExpression as? KtNameReferenceExpression
                else -> null
            } ?: return false
        val resolved = resolveReference(reference)
        return when (resolved) {
            is PsiClass -> resolved.qualifiedName == ARMERIA_ROUTE_CLASS
            is KtClassOrObject -> resolved.fqName?.asString() == ARMERIA_ROUTE_CLASS
            null -> unwrapped.text == ARMERIA_ROUTE_CLASS
            else -> false
        }
    }

    private fun httpMethodLabel(expression: KtExpression): String? {
        if (ArmeriaKotlinExpressionSupport.extractKotlinStringConstant(expression) != null) {
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

    private fun resolveCallDeclaration(call: KtCallExpression): PsiElement? {
        val callee = call.calleeExpression ?: return null
        val reference =
            when (callee) {
                is KtNameReferenceExpression -> callee
                is KtDotQualifiedExpression -> callee.selectorExpression as? KtNameReferenceExpression
                else -> null
            } ?: return null
        return resolveReference(reference)
    }

    private fun resolveReference(reference: KtNameReferenceExpression): PsiElement? {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        return reference.references.firstNotNullOfOrNull { it.resolve() }
    }
}
