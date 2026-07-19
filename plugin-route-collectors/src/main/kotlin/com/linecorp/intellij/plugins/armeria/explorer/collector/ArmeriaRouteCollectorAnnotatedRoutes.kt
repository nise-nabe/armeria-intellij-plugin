package com.linecorp.intellij.plugins.armeria.explorer.collector
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaAnnotatedMetadataSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

object ArmeriaRouteCollectorAnnotatedRoutes {
    fun collectAnnotatedRoutesIndexed(
        project: com.intellij.openapi.project.Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val seenMethods = mutableSetOf<PsiMethod>()
        for (annotationFqn in ArmeriaRouteSupport.routeAnnotations.keys) {
            val annotationClass = psiFacade.findClass(annotationFqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope).forEach { method ->
                if (!seenMethods.add(method)) {
                    return@forEach
                }
                addAnnotatedRouteFromMethod(method, routes)
            }
        }
    }

    fun addAnnotatedRouteFromMethod(
        method: PsiMethod,
        routes: MutableList<ArmeriaRoute>,
    ) {
        val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return
        val containingClass = method.containingClass ?: return
        val classPrefix =
            ArmeriaRouteSupport.extractPrimaryPath(containingClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION))
        val classDecorators =
            ArmeriaRouteSupport.extractNames(containingClass.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
        val classExceptionHandlers =
            ArmeriaRouteSupport.extractNames(containingClass.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION))
        val paths =
            buildList {
                addAll(ArmeriaRouteSupport.extractPaths(annotation.first))
                addAll(ArmeriaRouteSupport.extractPathAnnotations(method))
            }.ifEmpty { listOf("/") }.distinct()
        val methodDecorators =
            classDecorators + ArmeriaRouteSupport.extractNames(method.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
        val methodExceptionHandlers =
            classExceptionHandlers +
                ArmeriaRouteSupport.extractNames(
                    method.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION),
                )
        val target = buildMethodTarget(containingClass, method)
        val executionHints = ArmeriaTimeoutSupport.collectExecutionHints(method)
        for (rawPath in paths) {
            val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
            val combinedPath = ArmeriaRouteSupport.combinePaths(classPrefix, normalizedPath)
            routes +=
                ArmeriaRoute.create(
                    element = method,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = annotation.second,
                    path = combinedPath,
                    target = target,
                    routeMatch = RouteMatch.ANNOTATED_HTTP,
                    pathType = pathType,
                    decorators = methodDecorators.distinct(),
                    exceptionHandlers = methodExceptionHandlers.distinct(),
                    executionHints = executionHints,
                    contentHints = ArmeriaAnnotatedMetadataSupport.collectContentHints(method, combinedPath, pathType),
                )
        }
    }

    private fun buildMethodTarget(
        psiClass: com.intellij.psi.PsiClass,
        method: PsiMethod,
    ): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }
}
