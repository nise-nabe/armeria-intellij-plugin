package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinMethodRoute
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument

internal object ArmeriaKotlinRouteNavigationSupport {
    fun annotatedRouteHandler(element: PsiElement): PsiElement? =
        annotatedKotlinRouteFunction(element)

    fun annotatedKotlinRouteFunction(element: PsiElement): PsiElement? {
        val function = kotlinFunctionFromElement(element) ?: return null
        return function.takeIf { ArmeriaKotlinMethodRoute.from(it) != null }
    }

    fun httpMethod(handler: PsiElement): String? =
        (handler as? KtNamedFunction)?.let { ArmeriaKotlinMethodRoute.from(it)?.httpMethod }

    fun routePath(handler: PsiElement): String =
        (handler as? KtNamedFunction)?.let { ArmeriaKotlinMethodRoute.from(it)?.paths?.joinToString(", ") }.orEmpty()

    fun relatedRegistrations(handler: PsiElement): List<PsiElement> {
        val function = handler as? KtNamedFunction ?: return emptyList()
        val serviceClass = PsiTreeUtil.getParentOfType(function, KtClassOrObject::class.java)?.toLightClass()
            ?: return emptyList()
        return ArmeriaRouteNavigationSupport.findRegistrationsForServiceClass(serviceClass, function.project)
    }

    fun relatedHandlers(context: PsiElement): List<PsiElement> {
        val call = kotlinRegistrationCallFromContext(context) ?: return emptyList()
        val serviceClass = resolveServiceClassFromKotlinCall(call) ?: return emptyList()
        return ArmeriaRouteNavigationSupport.annotatedHandlersInClass(serviceClass)
    }

    fun collectRegistrationFromReference(
        element: PsiElement,
        serviceClass: PsiClass,
        registrations: MutableSet<PsiElement>,
    ) {
        val kotlinCall = kotlinRegistrationCallFromReference(element) ?: return
        val resolvedClass = resolveServiceClassFromKotlinCall(kotlinCall) ?: return
        if (ArmeriaRouteNavigationSupport.serviceTypesMatch(serviceClass, resolvedClass)) {
            registrations += kotlinCall
        }
    }

    fun annotatedHandlerFromMethod(method: PsiMethod): PsiElement? {
        val kotlinFunction = method.originalElement as? KtNamedFunction ?: return null
        return kotlinFunction.takeIf { ArmeriaKotlinMethodRoute.from(it) != null }
    }

    fun resolvedClassFromReference(expression: PsiElement): PsiClass? = when (expression) {
        is KtNameReferenceExpression -> classFromResolved(expression.references.firstOrNull()?.resolve())
        else -> null
    }

    fun classFromResolved(resolved: PsiElement?): PsiClass? = when (resolved) {
        is PsiClass -> resolved
        is PsiMethod -> resolved.takeIf { it.isConstructor }?.containingClass
        is PsiVariable -> (resolved.type as? com.intellij.psi.PsiClassType)?.resolve()
        is KtClassOrObject -> resolved.toLightClass()
        is KtConstructor<*> -> resolved.getContainingClassOrObject().toLightClass()
        is KtProperty -> classFromKotlinProperty(resolved)
        else -> null
    }

    fun resolveServiceClassFromImplementation(expression: PsiElement): PsiClass? {
        resolvedClassFromReference(expression)?.let { return it }
        when (expression) {
            is KtCallExpression -> {
                val reference = when (val callee = expression.calleeExpression) {
                    is KtNameReferenceExpression -> callee
                    is KtDotQualifiedExpression -> callee.selectorExpression as? KtNameReferenceExpression
                    else -> null
                }
                classFromResolved(reference?.references?.firstOrNull()?.resolve())?.let { return it }
            }
            is KtProperty -> return classFromKotlinProperty(expression)
        }
        return null
    }

    fun isKotlinServiceRegistrationCall(call: PsiElement): Boolean {
        val kotlinCall = call as? KtCallExpression ?: return false
        val methodName = kotlinCallName(kotlinCall) ?: return false
        if (methodName !in ArmeriaRouteNavigationSupport.serviceRegistrationMethodNames) {
            return false
        }
        return ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(kotlinCall)
    }

    fun registrationNavigationElement(registration: PsiElement): PsiElement {
        val call = registration as? KtCallExpression ?: return registration
        return kotlinCallReferenceNameElement(call) ?: registration
    }

    fun handlerNavigationElement(handler: PsiElement): PsiElement {
        val function = handler as? KtNamedFunction ?: return handler
        return function.nameIdentifier ?: handler
    }

    private fun kotlinFunctionFromElement(element: PsiElement): KtNamedFunction? = when (element) {
        is KtNamedFunction -> element
        else -> PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java, false)
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

    private fun kotlinRegistrationCallFromContext(context: PsiElement): KtCallExpression? {
        if (context is KtCallExpression && isKotlinServiceRegistrationCall(context)) {
            return context
        }
        return PsiTreeUtil.getParentOfType(context, KtCallExpression::class.java)
            ?.takeIf(::isKotlinServiceRegistrationCall)
    }

    private fun resolveServiceClassFromKotlinCall(call: KtCallExpression): PsiClass? {
        val implementation = kotlinServiceImplementationExpression(call) ?: return null
        resolveServiceClassFromImplementation(implementation)?.let { return it }
        return ArmeriaRouteNavigationSupport.findClassByTarget(
            call.project,
            extractKotlinRouteTarget(implementation),
        )
    }

    private fun classFromKotlinProperty(property: KtProperty): PsiClass? {
        property.typeReference?.references?.firstOrNull()?.resolve()?.let { typeResolved ->
            when (typeResolved) {
                is PsiClass -> return typeResolved
                is KtClassOrObject -> return typeResolved.toLightClass()
            }
        }
        val initializer = property.initializer ?: return null
        if (initializer is KtCallExpression) {
            val reference = when (val callee = initializer.calleeExpression) {
                is KtNameReferenceExpression -> callee
                is KtDotQualifiedExpression -> callee.selectorExpression as? KtNameReferenceExpression
                else -> null
            }
            classFromResolved(reference?.references?.firstOrNull()?.resolve())?.let { return it }
        }
        return ArmeriaRouteNavigationSupport.findClassByTarget(
            property.project,
            extractKotlinRouteTarget(initializer),
        )
    }

    private fun extractKotlinRouteTarget(expression: KtExpression): String {
        return when (expression) {
            is KtCallExpression -> {
                val callee = expression.calleeExpression
                val reference = when (callee) {
                    is KtNameReferenceExpression -> callee
                    is KtDotQualifiedExpression -> callee.selectorExpression as? KtNameReferenceExpression
                    else -> null
                }
                reference?.getReferencedName() ?: expression.text.trim()
            }
            is KtNameReferenceExpression -> expression.getReferencedName()
            else -> expression.text.trim()
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

    private fun kotlinCallReferenceNameElement(call: KtCallExpression): PsiElement? {
        return when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.selectorExpression
            is KtNameReferenceExpression -> callee
            else -> null
        }
    }
}
