package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

internal data class SpringMvcRoute(
    val httpMethod: String,
    val path: String,
    val target: String,
    val element: PsiMethod,
)

internal object ArmeriaSpringMvcRouteCollector {
    private const val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"
    private const val CONTROLLER = "org.springframework.stereotype.Controller"
    private const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"

    private val CONTROLLER_STEREOTYPES = listOf(REST_CONTROLLER, CONTROLLER)

    private val MAPPING_ANNOTATIONS = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to "GET",
        "org.springframework.web.bind.annotation.PostMapping" to "POST",
        "org.springframework.web.bind.annotation.PutMapping" to "PUT",
        "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
        "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
        REQUEST_MAPPING to "",
    )

    private fun isSpringWebAvailable(psiFacade: JavaPsiFacade, scope: GlobalSearchScope): Boolean {
        return psiFacade.findClass(REST_CONTROLLER, scope) != null ||
            psiFacade.findClass(CONTROLLER, scope) != null
    }

    fun collect(project: Project, scope: GlobalSearchScope): List<SpringMvcRoute> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        if (!isSpringWebAvailable(psiFacade, scope)) {
            return emptyList()
        }
        val controllers = linkedSetOf<PsiClass>()
        for (stereotypeFqn in CONTROLLER_STEREOTYPES) {
            val annotationClass = psiFacade.findClass(stereotypeFqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { controllers.add(it) }
        }
        val routes = mutableListOf<SpringMvcRoute>()
        for (controller in controllers) {
            collectFromController(controller, routes)
        }
        return routes
    }

    private fun collectFromController(controller: PsiClass, routes: MutableList<SpringMvcRoute>) {
        val classPrefixes = extractClassRequestMappingPrefixes(controller)
        for (method in controller.methods) {
            // Inherited / interface-declared mappings are deferred (see #261).
            if (method.containingClass != controller) {
                continue
            }
            for (annotation in method.annotations) {
                val fqn = annotation.qualifiedName ?: continue
                val defaultMethod = MAPPING_ANNOTATIONS[fqn] ?: continue
                addRoutesFromMethod(method, annotation, defaultMethod, classPrefixes, routes)
            }
        }
    }

    private fun addRoutesFromMethod(
        method: PsiMethod,
        mappingAnnotation: PsiAnnotation,
        defaultMethod: String,
        classPrefixes: List<String>,
        routes: MutableList<SpringMvcRoute>,
    ) {
        val containingClass = method.containingClass ?: return
        val httpMethods = resolveHttpMethods(mappingAnnotation, defaultMethod)
        val paths = ArmeriaRouteSupport.extractPaths(mappingAnnotation).ifEmpty { listOf("") }
        val target = buildMethodTarget(containingClass, method)
        for (classPrefix in classPrefixes) {
            for (rawPath in paths) {
                val combinedPath = ArmeriaRouteSupport.combinePaths(classPrefix, rawPath)
                for (httpMethod in httpMethods) {
                    routes += SpringMvcRoute(
                        httpMethod = httpMethod,
                        path = combinedPath,
                        target = target,
                        element = method,
                    )
                }
            }
        }
    }

    private fun extractClassRequestMappingPrefixes(psiClass: PsiClass): List<String> {
        // @RequestMapping is @Repeatable — collect every class-level mapping, not only the first.
        val requestMappings = psiClass.annotations.filter { it.qualifiedName == REQUEST_MAPPING }
        if (requestMappings.isEmpty()) {
            return listOf("")
        }
        val prefixes = requestMappings.flatMap { mapping ->
            ArmeriaRouteSupport.extractPaths(mapping).ifEmpty { listOf("") }
        }
        return prefixes.ifEmpty { listOf("") }
    }

    /**
     * Returns one entry per HTTP method. Empty attribute (all methods) yields a single blank method
     * so callers keep catch-all semantics.
     */
    private fun resolveHttpMethods(annotation: PsiAnnotation, defaultMethod: String): List<String> {
        if (defaultMethod.isNotEmpty()) {
            return listOf(defaultMethod)
        }
        val methods = extractRequestMethods(annotation.findDeclaredAttributeValue("method"))
        return methods.ifEmpty { listOf("") }
    }

    /**
     * Resolves Spring `RequestMethod` enum references (and arrays) to HTTP method names.
     * Empty attribute means all methods — returns an empty list so callers keep `httpMethod` blank.
     */
    private fun extractRequestMethods(value: PsiAnnotationMemberValue?): List<String> {
        return when (value) {
            null -> emptyList()
            is PsiArrayInitializerMemberValue -> value.initializers.flatMap(::extractRequestMethods)
            else -> {
                val resolved = value.reference?.resolve()
                val name = (resolved as? PsiEnumConstant)?.name?.uppercase()
                if (!name.isNullOrEmpty()) listOf(name) else emptyList()
            }
        }
    }

    private fun buildMethodTarget(psiClass: PsiClass, method: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }
}
