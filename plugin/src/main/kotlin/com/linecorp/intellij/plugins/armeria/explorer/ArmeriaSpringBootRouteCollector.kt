package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

internal object ArmeriaSpringBootRouteCollector {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")
    private const val KOTLIN_LIGHT_METHOD_CLASS = "org.jetbrains.kotlin.asJava.elements.KtLightMethod"
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val beanAnnotation = psiFacade.findClass(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION, scope) ?: return
        val seenBeanMethods = mutableSetOf<PsiMethod>()
        AnnotatedElementsSearch.searchPsiMethods(beanAnnotation, scope).forEach { method ->
            if (!seenBeanMethods.add(method)) {
                return@forEach
            }
            if (!ArmeriaRouteSupport.isArmeriaServerBeanReturnType(method, scope)) {
                return@forEach
            }
            collectServiceRegistrationsFromBeanMethod(method, routes, seenServiceRegistrations)
        }
    }

    private fun collectServiceRegistrationsFromBeanMethod(
        method: PsiMethod,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val kotlinMethod = resolveKotlinOrigin(method)
        if (kotlinMethod != null && isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(
                kotlinMethod,
                routes,
                seenServiceRegistrations,
            )
            return
        }
        method.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                ArmeriaRouteCollector.collectServiceRegistrationFromMethodCall(
                    expression,
                    routes,
                    seenServiceRegistrations,
                )
                super.visitMethodCallExpression(expression)
            }
        })
    }

    private fun resolveKotlinOrigin(method: PsiMethod): PsiElement? {
        if (method.javaClass.name != KOTLIN_LIGHT_METHOD_CLASS) {
            return null
        }
        return try {
            method.javaClass.getMethod("getKotlinOrigin").invoke(method) as? PsiElement
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    private fun isKotlinPluginAvailable(): Boolean =
        PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
