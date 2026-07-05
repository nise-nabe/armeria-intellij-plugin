package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaRouteNavigationSupport {
    fun annotatedRouteMethod(element: PsiElement): PsiMethod? {
        val method = methodFromElement(element) ?: return null
        return method.takeIf { ArmeriaRouteSupport.findRouteAnnotation(it) != null }
    }

    fun httpMethod(method: PsiMethod): String? = ArmeriaRouteSupport.findRouteAnnotation(method)?.second

    fun routePath(method: PsiMethod): String {
        val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return ""
        val classPrefix = ArmeriaRouteSupport.extractPrimaryPath(
            method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
        )
        val path = ArmeriaRouteSupport.extractPaths(annotation.first).firstOrNull().orEmpty().ifBlank { "/" }
        return ArmeriaRouteSupport.combinePaths(classPrefix, ArmeriaRouteSupport.normalizePath(path))
    }

    fun relatedRegistrations(method: PsiMethod): List<PsiMethodCallExpression> {
        val serviceClass = method.containingClass ?: return emptyList()
        return findRegistrationsForServiceClass(serviceClass, method.project)
    }

    fun relatedHandlers(context: PsiElement): List<PsiMethod> {
        val registration = registrationCallFromContext(context) ?: return emptyList()
        val serviceClass = resolveServiceClass(registration) ?: return emptyList()
        return serviceClass.methods.filter { ArmeriaRouteSupport.findRouteAnnotation(it) != null }
    }

    fun relatedGotoItems(method: PsiMethod): List<GotoRelatedItem> =
        relatedRegistrations(method).map { GotoRelatedItem(it.methodExpression, message("editor.route.gotoRelated.registration")) }

    fun relatedHandlerGotoItems(context: PsiElement): List<GotoRelatedItem> =
        relatedHandlers(context).map { handler ->
            GotoRelatedItem(handler.nameIdentifier ?: handler, message("editor.route.gotoRelated.handler", httpMethod(handler).orEmpty(), routePath(handler)))
        }

    private fun methodFromElement(element: PsiElement): PsiMethod? = when (element) {
        is PsiMethod -> element
        is PsiIdentifier -> element.parent as? PsiMethod
        else -> PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
    }

    private fun findRegistrationsForServiceClass(serviceClass: PsiClass, project: Project): List<PsiMethodCallExpression> {
        val registrations = linkedSetOf<PsiMethodCallExpression>()
        ReferencesSearch.search(serviceClass, GlobalSearchScope.projectScope(project)).forEach { reference ->
            val call = PsiTreeUtil.getParentOfType(reference.element, PsiMethodCallExpression::class.java) ?: return@forEach
            if (!isServiceRegistrationCall(call)) return@forEach
            val resolvedClass = resolveServiceClass(call) ?: return@forEach
            if (resolvedClass == serviceClass || serviceClass.isInheritor(resolvedClass, true)) registrations += call
        }
        return registrations.toList()
    }

    private fun registrationCallFromContext(context: PsiElement): PsiMethodCallExpression? {
        if (context is PsiMethodCallExpression && isServiceRegistrationCall(context)) {
            return context
        }
        return PsiTreeUtil.getParentOfType(context, PsiMethodCallExpression::class.java)
            ?.takeIf(::isServiceRegistrationCall)
    }

    private fun isServiceRegistrationCall(call: PsiMethodCallExpression): Boolean =
        call.methodExpression.referenceName in SERVICE_REGISTRATION_METHOD_NAMES

    private fun resolveServiceClass(call: PsiMethodCallExpression): PsiClass? {
        val args = call.argumentList.expressions
        val expr = when (ServiceRegistrationMethod.fromMethodName(call.methodExpression.referenceName ?: return null)) {
            ServiceRegistrationMethod.ANNOTATED_SERVICE -> args.getOrNull(1) ?: args.getOrNull(0)
            ServiceRegistrationMethod.SERVICE, ServiceRegistrationMethod.SERVICE_UNDER -> args.getOrNull(1)
            else -> null
        } ?: return null
        return when (expr) {
            is PsiNewExpression -> expr.classReference?.resolve() as? PsiClass
            is PsiReferenceExpression -> when (val resolved = expr.resolve()) {
                is PsiClass -> resolved
                is PsiMethod -> resolved.containingClass
                else -> null
            }
            is PsiExpression -> (expr.type as? PsiClassType)?.resolve()
            else -> null
        }
    }

    private val SERVICE_REGISTRATION_METHOD_NAMES = setOf(
        ServiceRegistrationMethod.SERVICE.methodName,
        ServiceRegistrationMethod.SERVICE_UNDER.methodName,
        ServiceRegistrationMethod.ANNOTATED_SERVICE.methodName,
    )
}
