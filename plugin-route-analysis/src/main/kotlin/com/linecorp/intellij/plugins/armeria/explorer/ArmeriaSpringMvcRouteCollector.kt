package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
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

    private val MAPPING_ANNOTATIONS = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to "GET",
        "org.springframework.web.bind.annotation.PostMapping" to "POST",
        "org.springframework.web.bind.annotation.PutMapping" to "PUT",
        "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
        "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
        REQUEST_MAPPING to "",
    )

    fun isSpringWebAvailable(psiFacade: JavaPsiFacade, scope: GlobalSearchScope): Boolean {
        return psiFacade.findClass(REST_CONTROLLER, scope) != null ||
            psiFacade.findClass(CONTROLLER, scope) != null
    }

    fun collect(project: Project, scope: GlobalSearchScope): List<SpringMvcRoute> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        if (!isSpringWebAvailable(psiFacade, scope)) {
            return emptyList()
        }
        val routes = mutableListOf<SpringMvcRoute>()
        val seenMethods = mutableSetOf<PsiMethod>()
        for ((annotationFqn, defaultMethod) in MAPPING_ANNOTATIONS) {
            val annotationClass = psiFacade.findClass(annotationFqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope).forEach { method ->
                if (!seenMethods.add(method)) {
                    return@forEach
                }
                addRoutesFromMethod(method, defaultMethod, routes)
            }
        }
        return routes
    }

    private fun addRoutesFromMethod(
        method: PsiMethod,
        defaultMethod: String,
        routes: MutableList<SpringMvcRoute>,
    ) {
        val containingClass = method.containingClass ?: return
        if (!isSpringController(containingClass)) {
            return
        }
        val classPrefix = extractClassRequestMappingPrefix(containingClass)
        val mappingAnnotation = findMappingAnnotation(method) ?: return
        val httpMethod = resolveHttpMethod(mappingAnnotation, defaultMethod)
        val paths = ArmeriaRouteSupport.extractPaths(mappingAnnotation).ifEmpty { listOf("") }
        val target = buildMethodTarget(containingClass, method)
        for (rawPath in paths) {
            routes += SpringMvcRoute(
                httpMethod = httpMethod,
                path = ArmeriaRouteSupport.combinePaths(classPrefix, ArmeriaRouteSupport.normalizePath(rawPath)),
                target = target,
                element = method,
            )
        }
    }

    private fun isSpringController(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName == REST_CONTROLLER || qualifiedName == CONTROLLER
        }
    }

    private fun extractClassRequestMappingPrefix(psiClass: PsiClass): String {
        val requestMapping = psiClass.annotations.firstOrNull { it.qualifiedName == REQUEST_MAPPING } ?: return ""
        return ArmeriaRouteSupport.extractPaths(requestMapping).firstOrNull().orEmpty()
    }

    private fun findMappingAnnotation(method: PsiMethod): PsiAnnotation? {
        return method.annotations.firstOrNull { annotation ->
            annotation.qualifiedName in MAPPING_ANNOTATIONS.keys
        }
    }

    private fun resolveHttpMethod(annotation: PsiAnnotation, defaultMethod: String): String {
        if (defaultMethod.isNotEmpty()) {
            return defaultMethod
        }
        val methods = extractRequestMethods(annotation.findDeclaredAttributeValue("method"))
        return methods.joinToString(", ")
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
                val name = value.text.trim().substringAfterLast('.').uppercase()
                if (name.isNotEmpty()) listOf(name) else emptyList()
            }
        }
    }

    private fun buildMethodTarget(psiClass: PsiClass, method: PsiMethod): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }
}
