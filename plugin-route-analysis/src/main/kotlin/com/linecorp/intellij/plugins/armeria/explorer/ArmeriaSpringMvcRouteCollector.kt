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
import com.intellij.psi.search.searches.ClassInheritorsSearch

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

    private val MAPPING_ANNOTATIONS =
        mapOf(
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
    private fun isSpringWebAvailable(
        psiFacade: JavaPsiFacade,
        classpathScope: GlobalSearchScope,
    ): Boolean =
        psiFacade.findClass(REST_CONTROLLER, classpathScope) != null ||
            psiFacade.findClass(CONTROLLER, classpathScope) != null

    fun collect(
        project: Project,
        scope: GlobalSearchScope,
    ): List<SpringMvcRoute> {
        val psiFacade = JavaPsiFacade.getInstance(project)
        // Library jars (spring-web / spring-context) are outside projectScope.
        val classpathScope = GlobalSearchScope.allScope(project)
        if (!isSpringWebAvailable(psiFacade, classpathScope)) {
            return emptyList()
        }
        // Direct stereotype hits plus concrete inheritors of abstract/interface stereotypes.
        // Mirrors Spring RequestMappingHandlerMapping.isHandler (TYPE_HIERARCHY for @Controller).
        val stereotypeTypes = linkedSetOf<PsiClass>()
        for (stereotypeFqn in CONTROLLER_STEREOTYPES) {
            val annotationClass = psiFacade.findClass(stereotypeFqn, classpathScope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).forEach { stereotypeTypes.add(it) }
        }
        val controllers = linkedSetOf<PsiClass>()
        for (type in stereotypeTypes) {
            if (isConcreteControllerType(type)) {
                controllers.add(type)
                continue
            }
            ClassInheritorsSearch.search(type, scope, true).forEach { inheritor ->
                if (isConcreteControllerType(inheritor)) {
                    controllers.add(inheritor)
                }
            }
        }
        val routes = mutableListOf<SpringMvcRoute>()
        for (controller in controllers) {
            collectFromController(controller, routes)
        }
        return routes
    }

    /** Spring does not instantiate abstract / interface stereotype types as handler beans. */
    private fun isConcreteControllerType(psiClass: PsiClass): Boolean =
        !psiClass.isInterface &&
            !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !psiClass.name.isNullOrEmpty()

    private fun collectFromController(
        controller: PsiClass,
        routes: MutableList<SpringMvcRoute>,
    ) {
        val hierarchy = typesInHierarchy(controller)
        val classPrefixes = extractMergedClassRequestMappingPrefixes(hierarchy)
        // One most-specific method per visible signature; unannotated overrides resolve via supers.
        for (signature in controller.visibleSignatures) {
            val method = signature.method
            if (method.containingClass?.qualifiedName == JAVA_LANG_OBJECT) {
                continue
            }
            val mappedMethod = resolveMappedMethod(hierarchy, method) ?: continue
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
     * Walks [hierarchy] (Spring TYPE_HIERARCHY order) and returns the first same-signature method
     * that declares a Spring MVC mapping. Unannotated intermediate overrides do not stop the search.
     */
    private fun resolveMappedMethod(
        hierarchy: List<PsiClass>,
        method: PsiMethod,
    ): PsiMethod? {
        for (type in hierarchy) {
            val candidate = type.findMethodBySignature(method, false) ?: continue
            if (hasMappingAnnotation(candidate)) {
                return candidate
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
                    routes +=
                        SpringMvcRoute(
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
     * Walks [hierarchy] (Spring TYPE_HIERARCHY DFS: interfaces before superclass). The first type
     * that declares `@RequestMapping` supplies prefixes (most specific wins; parent applies when
     * the child has none).
     */
    private fun extractMergedClassRequestMappingPrefixes(hierarchy: List<PsiClass>): List<String> {
        for (type in hierarchy) {
            val requestMappings = type.annotations.filter { it.qualifiedName == REQUEST_MAPPING }
            if (requestMappings.isEmpty()) {
                continue
            }
            val prefixes =
                requestMappings.flatMap { mapping ->
                    ArmeriaRouteSupport.extractPaths(mapping).ifEmpty { listOf("") }
                }
            return prefixes.ifEmpty { listOf("") }
        }
        return listOf("")
    }

    /**
     * Depth-first hierarchy walk matching Spring's AnnotationsScanner TYPE_HIERARCHY:
     * current type, then each interface (recursively), then superclass (recursively).
     */
    private fun typesInHierarchy(psiClass: PsiClass): List<PsiClass> {
        val visited = mutableSetOf<PsiClass>()
        val result = ArrayList<PsiClass>()

        fun walk(current: PsiClass) {
            if (current.qualifiedName == JAVA_LANG_OBJECT) {
                return
            }
            if (!visited.add(current)) {
                return
            }
            result.add(current)
            for (iface in current.interfaces) {
                walk(iface)
            }
            current.superClass?.let { walk(it) }
        }
        walk(psiClass)
        return result
    }

    private fun hasMappingAnnotation(method: PsiMethod): Boolean =
        method.annotations.any { annotation ->
            annotation.qualifiedName in MAPPING_ANNOTATIONS
        }

    /**
     * Returns one entry per HTTP method. Empty attribute (all methods) yields a single blank method
     * so callers keep catch-all semantics.
     */
    private fun resolveHttpMethods(
        annotation: PsiAnnotation,
        defaultMethod: String,
    ): List<String> {
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
    private fun extractRequestMethods(value: PsiAnnotationMemberValue?): List<String> =
        when (value) {
            null -> emptyList()
            is PsiArrayInitializerMemberValue -> value.initializers.flatMap(::extractRequestMethods)
            else -> {
                val resolved = value.reference?.resolve()
                val name = (resolved as? PsiEnumConstant)?.name?.uppercase()
                if (!name.isNullOrEmpty()) listOf(name) else emptyList()
            }
        }

    private fun buildMethodTarget(
        psiClass: PsiClass,
        method: PsiMethod,
    ): String {
        val className = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        return "$className#${method.name}()"
    }
}
