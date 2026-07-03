package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import org.jetbrains.kotlin.asJava.elements.KtLightMethod

internal object ArmeriaSpringBootRouteCollector {
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
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectServiceRegistrationsFromBeanMethod(method, routes, seenServiceRegistrations)
        }
    }

    private fun collectServiceRegistrationsFromBeanMethod(
        method: PsiMethod,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        val kotlinMethod = (method as? KtLightMethod)?.kotlinOrigin
        if (kotlinMethod != null) {
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
}
