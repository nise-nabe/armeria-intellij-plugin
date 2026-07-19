package com.linecorp.intellij.plugins.armeria.explorer.spring

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext

object ArmeriaSpringBootRouteCollector {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")
    private const val KOTLIN_LIGHT_METHOD_CLASS = "org.jetbrains.kotlin.asJava.elements.KtLightMethod"

    private val ktLightMethodClass: Class<*>? by lazy {
        if (!isKotlinPluginAvailable()) {
            return@lazy null
        }
        try {
            Class.forName(KOTLIN_LIGHT_METHOD_CLASS, false, ArmeriaSpringBootRouteCollector::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    private val getKotlinOriginMethod: java.lang.reflect.Method? by lazy {
        ktLightMethodClass?.getMethod("getKotlinOrigin")
    }

    fun collect(context: RouteCollectContext) {
        val psiFacade = JavaPsiFacade.getInstance(context.project)
        val beanAnnotation = psiFacade.findClass(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION, context.scope) ?: return
        val seenBeanMethods = mutableSetOf<PsiMethod>()
        AnnotatedElementsSearch.searchPsiMethods(beanAnnotation, context.scope).forEach { method ->
            if (!seenBeanMethods.add(method)) {
                return@forEach
            }
            if (!ArmeriaRouteSupport.isArmeriaServerBeanReturnType(method, context.scope)) {
                return@forEach
            }
            collectServiceRegistrationsFromBeanMethod(method, context.routes, context.seenServiceRegistrations, context)
        }
    }

    private fun collectServiceRegistrationsFromBeanMethod(
        method: PsiMethod,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
        context: RouteCollectContext,
    ) {
        if (isKotlinPluginAvailable()) {
            val kotlinMethod = resolveKotlinOrigin(method)
            if (kotlinMethod != null) {
                context.registration.collectServiceRegistrationsInScope(
                    kotlinMethod,
                    routes,
                    seenServiceRegistrations,
                )
                return
            }
        }
        method.body?.accept(
            object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    context.registration.collectServiceRegistrationFromMethodCall(
                        expression,
                        routes,
                        seenServiceRegistrations,
                    )
                    super.visitMethodCallExpression(expression)
                }
            },
        )
    }

    private fun resolveKotlinOrigin(method: PsiMethod): PsiElement? {
        val lightMethodClass = ktLightMethodClass ?: return null
        if (!lightMethodClass.isInstance(method)) {
            return null
        }
        val getKotlinOrigin = getKotlinOriginMethod ?: return null
        return try {
            getKotlinOrigin.invoke(method) as? PsiElement
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    private fun isKotlinPluginAvailable(): Boolean = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
