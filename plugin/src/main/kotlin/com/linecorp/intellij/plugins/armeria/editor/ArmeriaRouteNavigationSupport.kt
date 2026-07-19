package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.extractArmeriaRouteTarget
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaRouteNavigationSupport {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    val serviceRegistrationMethodNames: Set<String> = ServiceRegistrationMethod.CORE_METHOD_NAMES

    fun annotatedRouteHandler(element: PsiElement): PsiElement? {
        annotatedRouteMethod(element)?.let { return it }
        if (isKotlinPluginAvailable()) {
            return ArmeriaKotlinRouteNavigationSupport.annotatedRouteHandler(element)
        }
        return null
    }

    fun annotatedRouteMethod(element: PsiElement): PsiMethod? {
        val method = methodFromElement(element) ?: return null
        return method.takeIf { ArmeriaRouteSupport.findRouteAnnotation(it) != null }
    }

    fun annotatedKotlinRouteFunction(element: PsiElement): PsiElement? {
        if (!isKotlinPluginAvailable()) {
            return null
        }
        return ArmeriaKotlinRouteNavigationSupport.annotatedKotlinRouteFunction(element)
    }

    fun httpMethod(handler: PsiElement): String? =
        when (handler) {
            is PsiMethod -> ArmeriaRouteSupport.findRouteAnnotation(handler)?.second
            else -> if (isKotlinPluginAvailable()) ArmeriaKotlinRouteNavigationSupport.httpMethod(handler) else null
        }

    fun routePath(handler: PsiElement): String =
        when (handler) {
            is PsiMethod -> routePathsForJavaMethod(handler).joinToString(", ")
            else -> if (isKotlinPluginAvailable()) ArmeriaKotlinRouteNavigationSupport.routePath(handler) else ""
        }

    fun relatedRegistrations(handler: PsiElement): List<PsiElement> {
        if (isIndexUnavailable(handler.project)) {
            return emptyList()
        }
        return try {
            when (handler) {
                is PsiMethod ->
                    findRegistrationsForServiceClass(
                        handler.containingClass ?: return emptyList(),
                        handler.project,
                    )
                else ->
                    if (isKotlinPluginAvailable()) {
                        ArmeriaKotlinRouteNavigationSupport.relatedRegistrations(handler)
                    } else {
                        emptyList()
                    }
            }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    fun relatedHandlers(context: PsiElement): List<PsiElement> {
        if (isIndexUnavailable(context.project)) {
            return emptyList()
        }
        return try {
            javaRegistrationCallFromContext(context)?.let { call ->
                val serviceClass = resolveServiceClassFromJavaCall(call) ?: return emptyList()
                return annotatedHandlersInClass(serviceClass)
            }
            if (isKotlinPluginAvailable()) {
                ArmeriaKotlinRouteNavigationSupport.relatedHandlers(context)
            } else {
                emptyList()
            }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    fun relatedGotoItems(handler: PsiElement): List<GotoRelatedItem> =
        relatedRegistrations(handler).map { registration ->
            GotoRelatedItem(
                registrationNavigationElement(registration),
                message("editor.route.gotoRelated.registration"),
            )
        }

    fun relatedHandlerGotoItems(context: PsiElement): List<GotoRelatedItem> =
        relatedHandlers(context).map { handler ->
            val label =
                message(
                    "editor.route.gotoRelated.handler",
                    httpMethod(handler).orEmpty(),
                    routePath(handler),
                )
            object : GotoRelatedItem(
                handlerNavigationElement(handler),
                message("editor.route.gotoRelated.handlers"),
            ) {
                override fun getCustomName(): String = label
            }
        }

    fun findRegistrationsForServiceClass(
        serviceClass: PsiClass,
        project: Project,
    ): List<PsiElement> {
        if (isIndexUnavailable(project)) {
            return emptyList()
        }
        val registrations = linkedSetOf<PsiElement>()
        val scope = GlobalSearchScope.projectScope(project)
        val builderClass =
            JavaPsiFacade
                .getInstance(project)
                .findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope)
                ?: return emptyList()
        try {
            for (methodName in serviceRegistrationMethodNames) {
                for (method in builderClass.findMethodsByName(methodName, false)) {
                    ReferencesSearch.search(method, scope).forEach { reference ->
                        collectRegistrationFromReference(reference.element, serviceClass, registrations)
                    }
                }
            }
        } catch (_: IndexNotReadyException) {
            return emptyList()
        }
        return registrations.toList()
    }

    fun annotatedHandlersInClass(serviceClass: PsiClass): List<PsiElement> {
        val handlers = linkedSetOf<PsiElement>()
        var current: PsiClass? = serviceClass
        while (current != null) {
            for (method in current.methods) {
                if (isKotlinPluginAvailable()) {
                    ArmeriaKotlinRouteNavigationSupport.annotatedHandlerFromMethod(method)?.let {
                        handlers += it
                        continue
                    }
                }
                if (ArmeriaRouteSupport.findRouteAnnotation(method) != null) {
                    handlers += method
                }
            }
            current = current.superClass
        }
        return handlers.toList()
    }

    fun serviceTypesMatch(
        first: PsiClass,
        second: PsiClass,
    ): Boolean = first == second || first.isInheritor(second, true) || second.isInheritor(first, true)

    fun findClassByFqn(
        project: Project,
        target: String,
    ): PsiClass? {
        if (target.isBlank() || !target.contains('.')) {
            return null
        }
        return JavaPsiFacade.getInstance(project).findClass(target, GlobalSearchScope.projectScope(project))
    }

    fun findClassByTarget(
        project: Project,
        target: String,
    ): PsiClass? {
        if (target.isBlank()) {
            return null
        }
        if (target.contains('.')) {
            return findClassByFqn(project, target)
        }
        val scope = GlobalSearchScope.projectScope(project)
        return PsiShortNamesCache.getInstance(project).getClassesByName(target, scope).singleOrNull()
    }

    private fun routePathsForJavaMethod(method: PsiMethod): List<String> {
        val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return emptyList()
        val classPrefix =
            ArmeriaRouteSupport.extractPrimaryPath(
                method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
            )
        return buildList {
            addAll(ArmeriaRouteSupport.extractPaths(annotation.first))
            addAll(ArmeriaRouteSupport.extractPathAnnotations(method))
        }.ifEmpty { listOf("/") }
            .distinct()
            .map { path -> ArmeriaRouteSupport.formatAnnotatedHandlerPath(classPrefix, path) }
    }

    private fun methodFromElement(element: PsiElement): PsiMethod? =
        when (element) {
            is PsiMethod -> element
            is PsiIdentifier -> element.parent as? PsiMethod
            else -> PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        }

    private fun collectRegistrationFromReference(
        element: PsiElement,
        serviceClass: PsiClass,
        registrations: MutableSet<PsiElement>,
    ) {
        val javaCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false)
        if (javaCall != null && isJavaServiceRegistrationCall(javaCall)) {
            val resolvedClass = resolveServiceClassFromJavaCall(javaCall) ?: return
            if (serviceTypesMatch(serviceClass, resolvedClass)) {
                registrations += javaCall
            }
            return
        }
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteNavigationSupport.collectRegistrationFromReference(element, serviceClass, registrations)
        }
    }

    private fun javaRegistrationCallFromContext(context: PsiElement): PsiMethodCallExpression? {
        if (context is PsiMethodCallExpression && isJavaServiceRegistrationCall(context)) {
            return context
        }
        return PsiTreeUtil
            .getParentOfType(context, PsiMethodCallExpression::class.java)
            ?.takeIf(::isJavaServiceRegistrationCall)
    }

    private fun isJavaServiceRegistrationCall(call: PsiMethodCallExpression): Boolean {
        val methodName = call.methodExpression.referenceName ?: return false
        if (methodName !in serviceRegistrationMethodNames) {
            return false
        }
        return ArmeriaRouteCollector.looksLikeArmeriaBuilderCall(call)
    }

    private fun resolveServiceClassFromJavaCall(call: PsiMethodCallExpression): PsiClass? {
        val implementation = javaServiceImplementationExpression(call) ?: return null
        return resolveServiceClassFromImplementation(implementation, call.project)
    }

    private fun resolveServiceClassFromImplementation(
        expression: PsiElement,
        project: Project,
    ): PsiClass? {
        resolvedClassFromReference(expression)?.let { return it }
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteNavigationSupport.resolveServiceClassFromImplementation(expression)?.let { return it }
        }
        val psiExpression = expression as? PsiExpression ?: return null
        classFromNewExpression(psiExpression)?.let { return it }
        return findClassByTarget(project, extractArmeriaRouteTarget(psiExpression))
    }

    private fun classFromNewExpression(expression: PsiExpression): PsiClass? {
        var current: PsiExpression = expression
        while (true) {
            when (current) {
                is PsiNewExpression -> return classFromResolved(current.classReference?.resolve())
                is PsiTypeCastExpression -> current = current.operand ?: return null
                is PsiParenthesizedExpression -> current = current.expression ?: return null
                else -> return null
            }
        }
    }

    private fun resolvedClassFromReference(expression: PsiElement): PsiClass? {
        when (expression) {
            is PsiReferenceExpression -> return classFromResolved(expression.resolve())
        }
        if (isKotlinPluginAvailable()) {
            return ArmeriaKotlinRouteNavigationSupport.resolvedClassFromReference(expression)
        }
        return null
    }

    private fun classFromResolved(resolved: PsiElement?): PsiClass? {
        when (resolved) {
            is PsiClass -> return resolved
            is PsiMethod -> return resolved.takeIf { it.isConstructor }?.containingClass
            is PsiVariable -> return (resolved.type as? PsiClassType)?.resolve()
        }
        if (isKotlinPluginAvailable()) {
            return ArmeriaKotlinRouteNavigationSupport.classFromResolved(resolved)
        }
        return null
    }

    private fun javaServiceImplementationExpression(call: PsiMethodCallExpression): PsiExpression? {
        val args = call.argumentList.expressions
        return when (ServiceRegistrationMethod.fromMethodName(call.methodExpression.referenceName ?: return null)) {
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> args.getOrNull(1) ?: args.getOrNull(0)
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER -> args.getOrNull(1)
            else -> null
        }
    }

    private fun registrationNavigationElement(registration: PsiElement): PsiElement =
        when (registration) {
            is PsiMethodCallExpression -> registration.methodExpression
            else ->
                if (isKotlinPluginAvailable()) {
                    ArmeriaKotlinRouteNavigationSupport.registrationNavigationElement(registration)
                } else {
                    registration
                }
        }

    private fun handlerNavigationElement(handler: PsiElement): PsiElement =
        when (handler) {
            is PsiMethod -> handler.nameIdentifier ?: handler
            else ->
                if (isKotlinPluginAvailable()) {
                    ArmeriaKotlinRouteNavigationSupport.handlerNavigationElement(handler)
                } else {
                    handler
                }
        }

    private fun isKotlinPluginAvailable(): Boolean = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)

    private fun isIndexUnavailable(project: Project): Boolean = DumbService.isDumb(project)
}
