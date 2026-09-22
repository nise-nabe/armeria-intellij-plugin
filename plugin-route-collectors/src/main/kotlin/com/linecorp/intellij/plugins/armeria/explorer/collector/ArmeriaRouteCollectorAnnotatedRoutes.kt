package com.linecorp.intellij.plugins.armeria.explorer.collector
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaAnnotatedMetadataSupport
import com.linecorp.intellij.plugins.armeria.explorer.collector.annotation.ArmeriaTimeoutSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteAnnotationSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

object ArmeriaRouteCollectorAnnotatedRoutes {
    fun collectAnnotatedRoutesIndexed(
        project: com.intellij.openapi.project.Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
    ) {
        val psiFacade = JavaPsiFacade.getInstance(project)
        // Annotation types live in library jars; resolve with allScope but search
        // annotated methods only in [scope] (project content).
        val classpathScope = GlobalSearchScope.allScope(project)
        val seenMethods = mutableSetOf<PsiMethod>()
        for (annotationFqn in ArmeriaRouteSupport.routeAnnotations.keys) {
            val annotationClass = psiFacade.findClass(annotationFqn, classpathScope) ?: continue
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
        val routeAnnotationPaths = ArmeriaRouteSupport.routeAnnotationPaths(method)
        if (routeAnnotationPaths.isEmpty()) {
            return
        }
        val containingClass = method.containingClass ?: return
        val classPrefixAnnotation = containingClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION)
        if (classPrefixAnnotation != null &&
            ArmeriaRouteAnnotationSupport.declaresUnresolvedPathArg(classPrefixAnnotation)
        ) {
            // An unresolvable class prefix would silently emit un-prefixed paths.
            return
        }
        val classPrefix = ArmeriaRouteSupport.extractPrimaryPath(classPrefixAnnotation)
        val classDecorators =
            ArmeriaRouteSupport.extractNames(containingClass.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
        val classExceptionHandlers =
            ArmeriaRouteSupport.extractNames(containingClass.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION))
        val methodDecorators =
            classDecorators + ArmeriaRouteSupport.extractNames(method.getAnnotation(ArmeriaRouteSupport.DECORATOR_ANNOTATION))
        val methodExceptionHandlers =
            classExceptionHandlers +
                ArmeriaRouteSupport.extractNames(
                    method.getAnnotation(ArmeriaRouteSupport.EXCEPTION_HANDLER_ANNOTATION),
                )
        val target = buildMethodTarget(containingClass, method)
        val executionHints = ArmeriaTimeoutSupport.collectExecutionHints(method)
        for ((httpMethod, paths) in routeAnnotationPaths) {
            for (rawPath in paths) {
                val (pathType, normalizedPath) = ArmeriaRouteSupport.parsePathType(rawPath)
                val combinedPath = ArmeriaRouteSupport.combinePaths(classPrefix, normalizedPath)
                routes +=
                    ArmeriaRoute.create(
                        element = method,
                        protocol = ArmeriaAnnotatedMetadataSupport.protocol(method).presentableName(),
                        httpMethod = httpMethod,
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
    }

    private fun buildMethodTarget(
        psiClass: com.intellij.psi.PsiClass,
        method: PsiMethod,
    ): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }
}
