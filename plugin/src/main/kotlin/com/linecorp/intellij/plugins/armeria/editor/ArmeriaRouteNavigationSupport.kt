package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.extractArmeriaRouteTarget
import com.linecorp.intellij.plugins.armeria.explorer.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinMethodRoute
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument

internal object ArmeriaRouteNavigationSupport {
    fun annotatedRouteHandler(element: PsiElement): PsiElement? {
        annotatedRouteMethod(element)?.let { return it }
        annotatedKotlinRouteFunction(element)?.let { return it }
        return null
    }

    fun annotatedRouteMethod(element: PsiElement): PsiMethod? {
        val method = methodFromElement(element) ?: return null
        return method.takeIf { ArmeriaRouteSupport.findRouteAnnotation(it) != null }
    }

    fun annotatedKotlinRouteFunction(element: PsiElement): KtNamedFunction? {
        val function = kotlinFunctionFromElement(element) ?: return null
        return function.takeIf { ArmeriaKotlinMethodRoute.from(it) != null }
    }

    fun httpMethod(handler: PsiElement): String? = when (handler) {
        is PsiMethod -> ArmeriaRouteSupport.findRouteAnnotation(handler)?.second
        is KtNamedFunction -> ArmeriaKotlinMethodRoute.from(handler)?.httpMethod
        else -> null
    }

    fun routePath(handler: PsiElement): String = when (handler) {
        is PsiMethod -> routePathForJavaMethod(handler)
        is KtNamedFunction -> ArmeriaKotlinMethodRoute.from(handler)?.paths?.firstOrNull().orEmpty()
        else -> ""
    }

    fun relatedRegistrations(handler: PsiElement): List<PsiElement> = when (handler) {
        is PsiMethod -> findRegistrationsForServiceClass(handler.containingClass ?: return emptyList(), handler.project)
        is KtNamedFunction -> {
            val serviceClass = PsiTreeUtil.getParentOfType(handler, KtClass::class.java)?.toLightClass() ?: return emptyList()
            findRegistrationsForServiceClass(serviceClass, handler.project)
        }
        else -> emptyList()
    }

    fun relatedHandlers(context: PsiElement): List<PsiElement> {
        javaRegistrationCallFromContext(context)?.let { call ->
            val serviceClass = resolveServiceClassFromJavaCall(call) ?: return emptyList()
            return annotatedHandlersInClass(serviceClass)
        }
        kotlinRegistrationCallFromContext(context)?.let { call ->
            val serviceClass = resolveServiceClassFromKotlinCall(call) ?: return emptyList()
            return annotatedHandlersInClass(serviceClass)
        }
        return emptyList()
    }

    fun relatedGotoItems(handler: PsiElement): List<GotoRelatedItem> =
        relatedRegistrations(handler).map { registration ->
            GotoRelatedItem(registrationNavigationElement(registration), message("editor.route.gotoRelated.registration"))
        }

    fun relatedHandlerGotoItems(context: PsiElement): List<GotoRelatedItem> =
        relatedHandlers(context).map { handler ->
            GotoRelatedItem(
                handlerNavigationElement(handler),
                message("editor.route.gotoRelated.handler", httpMethod(handler).orEmpty(), routePath(handler)),
            )
        }

    private fun routePathForJavaMethod(method: PsiMethod): String {
        val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return ""
        val classPrefix = ArmeriaRouteSupport.extractPrimaryPath(
            method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
        )
        val path = ArmeriaRouteSupport.extractPaths(annotation.first).firstOrNull().orEmpty().ifBlank { "/" }
        return ArmeriaRouteSupport.combinePaths(classPrefix, ArmeriaRouteSupport.normalizePath(path))
    }

    private fun methodFromElement(element: PsiElement): PsiMethod? = when (element) {
        is PsiMethod -> element
        is PsiIdentifier -> element.parent as? PsiMethod
        else -> PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
    }

    private fun kotlinFunctionFromElement(element: PsiElement): KtNamedFunction? = when (element) {
        is KtNamedFunction -> element
        else -> PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java, false)
    }

    private fun findRegistrationsForServiceClass(serviceClass: PsiClass, project: Project): List<PsiElement> {
        val registrations = linkedSetOf<PsiElement>()
        val scope = GlobalSearchScope.projectScope(project)
        val searchTargets = mutableListOf<PsiElement>()
        searchTargets += serviceClass
        val ktClass = serviceClass.originalElement as? KtClass
        if (ktClass != null) {
            searchTargets += ktClass
            ktClass.primaryConstructor?.let { searchTargets += it }
        }
        for (target in searchTargets) {
            collectRegistrationsFromReferences(target, serviceClass, registrations)
        }
        return registrations.toList()
    }

    private fun collectRegistrationsFromReferences(
        target: PsiElement,
        serviceClass: PsiClass,
        registrations: MutableSet<PsiElement>,
    ) {
        ReferencesSearch.search(target, GlobalSearchScope.projectScope(target.project)).forEach { reference ->
            val javaCall = PsiTreeUtil.getParentOfType(reference.element, PsiMethodCallExpression::class.java)
            if (javaCall != null && isJavaServiceRegistrationCall(javaCall)) {
                val resolvedClass = resolveServiceClassFromJavaCall(javaCall) ?: return@forEach
                if (serviceTypesMatch(serviceClass, resolvedClass)) {
                    registrations += javaCall
                }
                return@forEach
            }
            val kotlinCall = kotlinRegistrationCallFromReference(reference.element)
            if (kotlinCall != null) {
                val resolvedClass = resolveServiceClassFromKotlinCall(kotlinCall) ?: return@forEach
                if (serviceTypesMatch(serviceClass, resolvedClass)) {
                    registrations += kotlinCall
                }
            }
        }
    }

    private fun kotlinRegistrationCallFromReference(start: PsiElement): KtCallExpression? {
        var current: PsiElement? = start
        while (current != null) {
            val call = PsiTreeUtil.getParentOfType(current, KtCallExpression::class.java, false)
                ?: break
            if (isKotlinServiceRegistrationCall(call)) {
                return call
            }
            current = call.parent
        }
        return null
    }

    private fun javaRegistrationCallFromContext(context: PsiElement): PsiMethodCallExpression? {
        if (context is PsiMethodCallExpression && isJavaServiceRegistrationCall(context)) {
            return context
        }
        return PsiTreeUtil.getParentOfType(context, PsiMethodCallExpression::class.java)
            ?.takeIf(::isJavaServiceRegistrationCall)
    }

    private fun kotlinRegistrationCallFromContext(context: PsiElement): KtCallExpression? {
        if (context is KtCallExpression && isKotlinServiceRegistrationCall(context)) {
            return context
        }
        return PsiTreeUtil.getParentOfType(context, KtCallExpression::class.java)
            ?.takeIf(::isKotlinServiceRegistrationCall)
    }

    private fun isJavaServiceRegistrationCall(call: PsiMethodCallExpression): Boolean {
        val methodName = call.methodExpression.referenceName ?: return false
        if (methodName !in SERVICE_REGISTRATION_METHOD_NAMES) {
            return false
        }
        return ArmeriaRouteCollector.looksLikeArmeriaBuilderCall(call)
    }

    private fun isKotlinServiceRegistrationCall(call: KtCallExpression): Boolean {
        val methodName = kotlinCallName(call) ?: return false
        if (methodName !in SERVICE_REGISTRATION_METHOD_NAMES) {
            return false
        }
        return ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(call)
    }

    private fun resolveServiceClassFromJavaCall(call: PsiMethodCallExpression): PsiClass? {
        val implementation = javaServiceImplementationExpression(call) ?: return null
        return resolveServiceClassFromImplementation(implementation, call.project)
    }

    private fun resolveServiceClassFromKotlinCall(call: KtCallExpression): PsiClass? {
        val implementation = kotlinServiceImplementationExpression(call) ?: return null
        return resolveServiceClassFromImplementation(implementation, call.project)
    }

    private fun resolveServiceClassFromImplementation(expression: PsiElement, project: Project): PsiClass? {
        if (expression is PsiExpression) {
            val target = extractArmeriaRouteTarget(expression)
            return findClassByTarget(project, target)
        }
        if (expression is KtExpression) {
            return resolveKotlinImplementationClass(expression, project)
        }
        return null
    }

    private fun resolveKotlinImplementationClass(expression: KtExpression, project: Project): PsiClass? {
        when (expression) {
            is KtCallExpression -> {
                val reference = when (val callee = expression.calleeExpression) {
                    is KtNameReferenceExpression -> callee
                    is KtDotQualifiedExpression -> callee.selectorExpression as? KtNameReferenceExpression
                    else -> null
                }
                classFromResolved(reference?.references?.firstOrNull()?.resolve(), project)?.let { return it }
            }
            is KtNameReferenceExpression -> {
                classFromResolved(expression.references.firstOrNull()?.resolve(), project)?.let { return it }
            }
        }
        return (expression as? PsiExpression)?.let {
            findClassByTarget(project, extractArmeriaRouteTarget(it))
        }
    }

    private fun classFromResolved(resolved: PsiElement?, project: Project): PsiClass? = when (resolved) {
        is PsiClass -> resolved
        is PsiMethod -> resolved.takeIf { it.isConstructor }?.containingClass
        is KtClass -> resolved.toLightClass()
        is KtConstructor<*> -> resolved.getContainingClassOrObject().toLightClass()
        else -> null
    }

    private fun findClassByTarget(project: Project, target: String): PsiClass? {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        facade.findClass(target, scope)?.let { return it }
        if (!target.contains('.')) {
            return facade.findClass("example.$target", scope)
        }
        return null
    }

    private fun annotatedHandlersInClass(serviceClass: PsiClass): List<PsiElement> {
        val handlers = linkedSetOf<PsiElement>()
        var current: PsiClass? = serviceClass
        while (current != null) {
            for (method in current.allMethods) {
                when {
                    ArmeriaRouteSupport.findRouteAnnotation(method) != null -> handlers += method
                    else -> (method.originalElement as? KtNamedFunction)
                        ?.takeIf { ArmeriaKotlinMethodRoute.from(it) != null }
                        ?.let { handlers += it }
                }
            }
            current = current.superClass
        }
        return handlers.toList()
    }

    private fun serviceTypesMatch(first: PsiClass, second: PsiClass): Boolean =
        first == second || first.isInheritor(second, true) || second.isInheritor(first, true)

    private fun javaServiceImplementationExpression(call: PsiMethodCallExpression): PsiExpression? {
        val args = call.argumentList.expressions
        return when (ServiceRegistrationMethod.fromMethodName(call.methodExpression.referenceName ?: return null)) {
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> args.getOrNull(1) ?: args.getOrNull(0)
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER -> args.getOrNull(1)
            else -> null
        }
    }

    private fun kotlinServiceImplementationExpression(call: KtCallExpression): KtExpression? {
        val arguments = call.valueArguments
        return when (ServiceRegistrationMethod.fromMethodName(kotlinCallName(call) ?: return null)) {
            ServiceRegistrationMethod.ANNOTATED_SERVICE ->
                findKotlinArgumentExpression(arguments, "service", 1)
                    ?: findKotlinArgumentExpression(arguments, "service", 0)
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER ->
                findKotlinArgumentExpression(arguments, "service", 1)
            else -> null
        }
    }

    private fun findKotlinArgumentExpression(
        arguments: List<KtValueArgument>,
        parameterName: String,
        positionalIndex: Int,
    ): KtExpression? {
        arguments.firstOrNull { it.getArgumentName()?.asName?.identifier == parameterName }
            ?.getArgumentExpression()
            ?.let { return it }
        return arguments.getOrNull(positionalIndex)?.getArgumentExpression()
    }

    private fun kotlinCallName(call: KtCallExpression): String? {
        return when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            is KtNameReferenceExpression -> callee.text
            else -> callee?.text
        }
    }

    private fun registrationNavigationElement(registration: PsiElement): PsiElement = when (registration) {
        is PsiMethodCallExpression -> registration.methodExpression
        is KtCallExpression -> kotlinCallReferenceNameElement(registration) ?: registration
        else -> registration
    }

    private fun handlerNavigationElement(handler: PsiElement): PsiElement = when (handler) {
        is PsiMethod -> handler.nameIdentifier ?: handler
        is KtNamedFunction -> handler.nameIdentifier ?: handler
        else -> handler
    }

    private fun kotlinCallReferenceNameElement(call: KtCallExpression): PsiElement? {
        return when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.selectorExpression
            is KtNameReferenceExpression -> callee
            else -> null
        }
    }

    private val SERVICE_REGISTRATION_METHOD_NAMES = ServiceRegistrationMethod.METHOD_NAMES
}
