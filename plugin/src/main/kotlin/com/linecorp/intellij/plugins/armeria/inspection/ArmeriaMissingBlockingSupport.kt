package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

internal data class ArmeriaBlockingCallFinding(
    val highlight: PsiElement,
    val methodName: String,
)

internal object ArmeriaMissingBlockingSupport {
    const val HTTP_SERVICE_CLASS = "com.linecorp.armeria.server.HttpService"
    const val ABSTRACT_HTTP_SERVICE_CLASS = "com.linecorp.armeria.server.AbstractHttpService"

    private val HTTP_SERVICE_HANDLER_METHODS =
        setOf(
            "serve",
            "doGet",
            "doPost",
            "doPut",
            "doDelete",
            "doHead",
            "doPatch",
            "doOptions",
            "doTrace",
            "doConnect",
            "doQuery",
        )

    fun shouldInspect(method: PsiMethod): Boolean = shouldInspect(method, ignoreClassBlocking = false)

    fun shouldInspect(
        method: PsiMethod,
        ignoreClassBlocking: Boolean,
    ): Boolean {
        if (method.isConstructor) {
            return false
        }
        if (hasBlockingOrNonBlocking(method)) {
            return false
        }
        if (!ignoreClassBlocking && method.containingClass?.let(::hasBlockingOrNonBlocking) == true) {
            return false
        }
        return ArmeriaRouteSupport.findRouteAnnotation(method) != null ||
            isGrpcServiceOverride(method) ||
            isHttpServiceOverride(method) ||
            isEventLoopDataFetcher(method)
    }

    fun findings(method: PsiMethod): List<ArmeriaBlockingCallFinding> {
        val body = method.body ?: return emptyList()
        return findingsIn(body, method)
    }

    fun findingsIn(
        scope: PsiElement,
        boundary: PsiElement,
    ): List<ArmeriaBlockingCallFinding> {
        val findings = mutableListOf<ArmeriaBlockingCallFinding>()
        scope.forEachDescendant { element ->
            val call = element as? PsiMethodCallExpression ?: return@forEachDescendant
            if (!isOnInspectedPath(boundary, call)) {
                return@forEachDescendant
            }
            val methodName = call.methodExpression.referenceName ?: return@forEachDescendant
            val resolved = call.resolveMethod()
            val ownerFqn = resolved?.containingClass?.qualifiedName
            if (!ArmeriaBlockingCallPatterns.isBlockingCall(
                    methodName = methodName,
                    ownerFqn = ownerFqn,
                    unresolved = resolved == null,
                    qualifierText = call.methodExpression.qualifierExpression?.text,
                    argumentCount = call.argumentList.expressionCount,
                )
            ) {
                return@forEachDescendant
            }
            findings +=
                ArmeriaBlockingCallFinding(
                    highlight = call.methodExpression.referenceNameElement ?: call,
                    methodName = methodName,
                )
        }
        return findings
    }

    fun quickFixes(method: PsiMethod): Array<LocalQuickFix> {
        if (!honorsBlockingAnnotation(method)) {
            return emptyArray()
        }
        val fixes = mutableListOf<LocalQuickFix>(ArmeriaAddBlockingAnnotationQuickFix.forMethod(method))
        if (shouldOfferClassFix(method)) {
            method.containingClass
                ?.takeUnless { it is PsiAnonymousClass }
                ?.let { fixes += ArmeriaAddBlockingAnnotationQuickFix.forClass(it) }
        }
        return fixes.toTypedArray()
    }

    fun honorsBlockingAnnotation(method: PsiMethod): Boolean =
        ArmeriaRouteSupport.findRouteAnnotation(method) != null || isGrpcServiceOverride(method)

    fun isDataFetcherGet(method: PsiMethod): Boolean {
        if (method.name != "get") {
            return false
        }
        return method.containingClass?.let(::isDataFetcherClass) == true
    }

    fun isDataFetcherClass(psiClass: PsiClass): Boolean = hierarchyContains(psiClass, ::isDataFetcherType)

    fun isGrpcServiceType(psiClass: PsiClass): Boolean {
        if (psiClass.name?.endsWith("ImplBase") == true) {
            return true
        }
        return psiClass.qualifiedName == "io.grpc.BindableService"
    }

    fun isHttpServiceType(psiClass: PsiClass): Boolean {
        val fqn = psiClass.qualifiedName
        return fqn == HTTP_SERVICE_CLASS || fqn == ABSTRACT_HTTP_SERVICE_CLASS
    }

    private fun shouldOfferClassFix(method: PsiMethod): Boolean {
        val cls = method.containingClass ?: return false
        if (cls is PsiAnonymousClass || hasBlockingOrNonBlocking(cls)) {
            return false
        }
        val inspectable = cls.methods.filter { shouldInspect(it, ignoreClassBlocking = true) }
        if (inspectable.size <= 1) {
            return false
        }
        return inspectable.all { findings(it).isNotEmpty() }
    }

    private fun isEventLoopDataFetcher(method: PsiMethod): Boolean {
        if (!isDataFetcherGet(method)) {
            return false
        }
        val cls = method.containingClass ?: return false
        return when (ArmeriaGraphqlBlockingSupport.blockingExecutorCovers(cls)) {
            GraphqlBlockingCoverage.HAS_EVENT_LOOP_REGISTRATION -> true
            GraphqlBlockingCoverage.ALL_BLOCKING_EXECUTOR,
            GraphqlBlockingCoverage.NOT_REGISTERED,
            -> false
        }
    }

    private fun isHttpServiceOverride(method: PsiMethod): Boolean {
        if (method.name !in HTTP_SERVICE_HANDLER_METHODS) {
            return false
        }
        if (method.findSuperMethods().isEmpty()) {
            return false
        }
        return hierarchyContains(method.containingClass, ::isHttpServiceType)
    }

    private fun hierarchyContains(
        start: PsiClass?,
        match: (PsiClass) -> Boolean,
    ): Boolean {
        if (start == null) {
            return false
        }
        val visited = mutableSetOf<PsiClass>()
        val queue = ArrayDeque<PsiClass>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            if (match(current)) {
                return true
            }
            current.supers.forEach { queue.add(it) }
        }
        return false
    }

    fun hasBlockingOrNonBlocking(method: PsiMethod): Boolean =
        method.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION) ||
            method.hasAnnotation(ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION)

    fun hasBlockingOrNonBlocking(psiClass: PsiClass): Boolean =
        psiClass.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION) ||
            psiClass.hasAnnotation(ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION)

    private fun isGrpcServiceOverride(method: PsiMethod): Boolean {
        if (method.findSuperMethods().isEmpty()) {
            return false
        }
        var current: PsiClass? = method.containingClass
        while (current != null) {
            if (isGrpcServiceType(current)) {
                return true
            }
            current.supers.firstOrNull(::isGrpcServiceType)?.let { return true }
            current = current.superClass
        }
        return false
    }

    private fun isDataFetcherType(psiClass: PsiClass): Boolean = psiClass.qualifiedName == ArmeriaGraphqlBlockingSupport.DATA_FETCHER_CLASS

    private fun isOnInspectedPath(
        boundary: PsiElement,
        element: PsiElement,
    ): Boolean {
        if (element == boundary) {
            return true
        }
        var current: PsiElement? = element.parent
        while (current != null && current != boundary) {
            when (current) {
                is PsiLambdaExpression, is PsiAnonymousClass -> return false
                is PsiClass, is PsiMethod -> return false
            }
            current = current.parent
        }
        return current == boundary
    }
}
