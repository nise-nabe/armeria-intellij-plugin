package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import java.util.ArrayDeque

internal data class SpringMvcRoute(
    val httpMethod: String,
    val path: String,
    val target: String,
    /** Method that owns the mapping annotation (may be on a base type or interface). */
    val element: PsiMethod,
    /** Stereotype-annotated concrete controller used for module attribution and target. */
    val controller: PsiClass,
)

internal object ArmeriaSpringMvcRouteCollector {
    private const val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"
    private const val CONTROLLER = "org.springframework.stereotype.Controller"
    private const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
    private const val JAVA_LANG_OBJECT = "java.lang.Object"

    private val CONTROLLER_STEREOTYPES = listOf(REST_CONTROLLER, CONTROLLER)

    private val MAPPING_ANNOTATIONS = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to "GET",
        "org.springframework.web.bind.annotation.PostMapping" to "POST",
        "org.springframework.web.bind.annotation.PutMapping" to "PUT",
        "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
        "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
        REQUEST_MAPPING to "",
    )

    /**
     * Stereotype annotation classes live in Spring library jars. Resolve them with [classpathScope]
     * (typically [GlobalSearchScope.allScope]); search annotated controllers only in [scope]
     * (typically project content) so library controllers are not pulled in.
     */
    private fun isSpringWebAvailable(psiFacade: JavaPsiFacade, classpathScope: GlobalSearchScope): Boolean {
        return psiFacade.findClass(REST_CONTROLLER, classpathScope) != null ||
            psiFacade.findClass(CONTROLLER, classpathScope) != null
    }

    fun collect(project: Project, scope: GlobalSearchScope): List<SpringMvcRoute> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        // Library jars (spring-web / spring-context) are outside projectScope.
        val classpathScope = GlobalSearchScope.allScope(project)
        if (!isSpringWebAvailable(psiFacade, classpathScope)) {
            return emptyList()
        }
        val controllers = linkedSetOf<PsiClass>()
        for (stereotypeFqn in CONTROLLER_STEREOTYPES) {
            val annotationClass = psiFacade.findClass(stereotypeFqn, classpathScope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { controllers.add(it) }
        }
        val routes = mutableListOf<SpringMvcRoute>()
        for (controller in controllers) {
            if (!isConcreteControllerBean(controller, controllers)) {
                continue
            }
            collectFromController(controller, routes)
        }
        return routes
    }

    /**
     * Spring does not instantiate abstract / interface stereotype types, and a superclass that is
     * itself stereotype-annotated would otherwise emit the same inherited mappings twice.
     */
    private fun isConcreteControllerBean(controller: PsiClass, allControllers: Set<PsiClass>): Boolean {
        if (controller.isInterface || controller.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false
        }
        return allControllers.none { other ->
            other != controller && other.isInheritor(controller, true)
        }
    }

    private fun collectFromController(controller: PsiClass, routes: MutableList<SpringMvcRoute>) {
        val classPrefixes = extractMergedClassRequestMappingPrefixes(controller)
        // One most-specific method per visible signature; unannotated overrides resolve via supers.
        for (signature in controller.visibleSignatures) {
            val method = signature.method
            if (method.containingClass?.qualifiedName == JAVA_LANG_OBJECT) {
                continue
            }
            val mappedMethod = resolveMappedMethod(method) ?: continue
            for (annotation in mappedMethod.annotations) {
                val fqn = annotation.qualifiedName ?: continue
                val defaultMethod = MAPPING_ANNOTATIONS[fqn] ?: continue
                addRoutesFromMethod(
                    controller = controller,
                    method = mappedMethod,
                    mappingAnnotation = annotation,
                    defaultMethod = defaultMethod,
                    classPrefixes = classPrefixes,
                    routes = routes,
                )
            }
        }
    }

    /**
     * Prefers a mapping on [method] itself; otherwise the nearest super method (base class or
     * interface) that declares a Spring MVC mapping annotation.
     */
    private fun resolveMappedMethod(method: PsiMethod): PsiMethod? {
        if (hasMappingAnnotation(method)) {
            return method
        }
        for (superMethod in method.findSuperMethods()) {
            if (hasMappingAnnotation(superMethod)) {
                return superMethod
            }
        }
        return null
    }

    private fun addRoutesFromMethod(
        controller: PsiClass,
        method: PsiMethod,
        mappingAnnotation: PsiAnnotation,
        defaultMethod: String,
        classPrefixes: List<String>,
        routes: MutableList<SpringMvcRoute>,
    ) {
        val httpMethods = resolveHttpMethods(mappingAnnotation, defaultMethod)
        val paths = ArmeriaRouteSupport.extractPaths(mappingAnnotation).ifEmpty { listOf("") }
        // Attribute the route to the stereotype-annotated controller, even when the mapping
        // lives on a base class or interface.
        val target = buildMethodTarget(controller, method)
        for (classPrefix in classPrefixes) {
            for (rawPath in paths) {
                val combinedPath = ArmeriaRouteSupport.combinePaths(classPrefix, rawPath)
                for (httpMethod in httpMethods) {
                    routes += SpringMvcRoute(
                        httpMethod = httpMethod,
                        path = combinedPath,
                        target = target,
                        element = method,
                        controller = controller,
                    )
                }
            }
        }
    }

    /**
     * Walks [controller] then supers/interfaces. The first type that declares `@RequestMapping`
     * supplies prefixes (most specific wins; parent applies when the child has none).
     */
    private fun extractMergedClassRequestMappingPrefixes(controller: PsiClass): List<String> {
        for (type in typesInHierarchy(controller)) {
            if (type.annotations.none { it.qualifiedName == REQUEST_MAPPING }) {
                continue
            }
            return extractClassRequestMappingPrefixes(type)
        }
        return listOf("")
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

    private fun typesInHierarchy(psiClass: PsiClass): Sequence<PsiClass> = sequence {
        val visited = mutableSetOf<PsiClass>()
        val queue = ArrayDeque<PsiClass>()
        queue.add(psiClass)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }
            if (current.qualifiedName == JAVA_LANG_OBJECT) {
                continue
            }
            yield(current)
            current.superClass?.let { queue.add(it) }
            queue.addAll(current.interfaces)
        }
    }

    private fun hasMappingAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            annotation.qualifiedName in MAPPING_ANNOTATIONS
        }
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
