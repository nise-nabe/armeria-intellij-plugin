package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Detects duplicate HTTP route registrations within the same module.
 *
 * Includes annotated HTTP methods, ServerBuilder `.service`, `.serviceUnder`, and
 * `.annotatedService` registrations, fluent `.route()` chains, and `.healthCheckService()`.
 * Excludes non-HTTP protocols such as gRPC and mount-only registrations such as file services.
 *
 * Cross-registration conflicts are detected, for example `@Get("/foo")` versus
 * `.service("/foo", …)`. In-class annotated duplicate HTTP routes are excluded because
 * [com.linecorp.intellij.plugins.armeria.inspection.ArmeriaDuplicateRouteInspection] and
 * [com.linecorp.intellij.plugins.armeria.inspection.ArmeriaDuplicateRouteKotlinInspection] cover them.
 */
object ArmeriaRouteDuplicateIndex {
    private val CHECKED_MATCHES = setOf(
        RouteMatch.ANNOTATED_HTTP,
        RouteMatch.ANNOTATED_SERVICE,
        RouteMatch.SERVICE,
        RouteMatch.SERVICE_UNDER,
        RouteMatch.ROUTE_FLUENT,
        RouteMatch.HEALTH_CHECK,
    )

    fun duplicateHitsInFile(project: Project, file: PsiFile): List<DuplicateRegistrationHit> {
        val virtualFile = file.virtualFile ?: return emptyList()
        return getIndex(project).hitsByVirtualFile[virtualFile].orEmpty()
    }

    internal fun duplicateGroups(project: Project): List<DuplicateRegistrationGroup> {
        return getIndex(project).groups
    }

    /** Exposed for regression tests that simulate duplicate [ArmeriaRoute] entries in a group. */
    internal fun duplicateHitsForGroups(groups: List<DuplicateRegistrationGroup>): Map<VirtualFile, List<DuplicateRegistrationHit>> =
        buildHitsByVirtualFile(groups)

    internal fun findDuplicateGroups(routes: List<ArmeriaRoute>): List<DuplicateRegistrationGroup> {
        val groups = mutableListOf<DuplicateRegistrationGroup>()
        for ((_, moduleRoutes) in routes.filter { it.routeMatch in CHECKED_MATCHES }.groupBy { it.moduleName }) {
            if (moduleRoutes.size < 2) {
                continue
            }
            for (component in findConnectedComponents(moduleRoutes)) {
                if (component.size < 2 || isInClassAnnotatedHttpOnly(component)) {
                    continue
                }
                groups += DuplicateRegistrationGroup(component)
            }
        }
        return groups
    }

    private fun getIndex(project: Project): DuplicateRegistrationIndex {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            val groups = findDuplicateGroups(ArmeriaRouteCollector.collect(project))
            CachedValueProvider.Result.create(
                DuplicateRegistrationIndex(
                    groups = groups,
                    hitsByVirtualFile = buildHitsByVirtualFile(groups),
                ),
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }

    private fun findConnectedComponents(routes: List<ArmeriaRoute>): List<List<ArmeriaRoute>> {
        val parent = IntArray(routes.size) { it }
        val size = IntArray(routes.size) { 1 }

        fun find(index: Int): Int {
            var root = index
            while (parent[root] != root) {
                root = parent[root]
            }
            var current = index
            while (parent[current] != root) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }

        fun union(first: Int, second: Int) {
            var firstRoot = find(first)
            var secondRoot = find(second)
            if (firstRoot == secondRoot) {
                return
            }
            if (size[firstRoot] < size[secondRoot]) {
                val smallerRoot = firstRoot
                firstRoot = secondRoot
                secondRoot = smallerRoot
            }
            parent[secondRoot] = firstRoot
            size[firstRoot] += size[secondRoot]
        }

        for (first in routes.indices) {
            for (second in first + 1 until routes.size) {
                if (routesOverlap(routes[first], routes[second]) && httpMethodsOverlap(routes[first], routes[second])) {
                    union(first, second)
                }
            }
        }

        return routes.indices
            .groupBy(::find)
            .values
            .map { indices -> indices.map { routes[it] } }
    }

    private fun isInClassAnnotatedHttpOnly(routes: List<ArmeriaRoute>): Boolean {
        if (routes.any { it.routeMatch != RouteMatch.ANNOTATED_HTTP }) {
            return false
        }
        var containingClass: PsiClass? = null
        for (route in routes) {
            val method = route.pointer.element as? PsiMethod ?: return false
            val routeClass = method.containingClass ?: return false
            if (containingClass == null) {
                containingClass = routeClass
            } else if (containingClass != routeClass) {
                return false
            }
        }
        return true
    }

    private fun routesOverlap(first: ArmeriaRoute, second: ArmeriaRoute): Boolean =
        virtualHostsOverlap(first, second) && pathsOverlap(first, second)

    private fun virtualHostsOverlap(first: ArmeriaRoute, second: ArmeriaRoute): Boolean =
        first.virtualHostName == second.virtualHostName

    private fun pathsOverlap(first: ArmeriaRoute, second: ArmeriaRoute): Boolean {
        val firstIsPrefixMount = isPrefixMount(first)
        val secondIsPrefixMount = isPrefixMount(second)
        return when {
            firstIsPrefixMount && secondIsPrefixMount ->
                pathIsUnder(first.path, prefixForMount(second)) ||
                    pathIsUnder(second.path, prefixForMount(first)) ||
                    prefixForMount(first) == prefixForMount(second)
            firstIsPrefixMount -> pathIsUnder(second.path, prefixForMount(first))
            secondIsPrefixMount -> pathIsUnder(first.path, prefixForMount(second))
            first.pathType == PathType.REGEX || second.pathType == PathType.REGEX ->
                first.path == second.path && first.pathType == second.pathType
            else -> first.path == second.path
        }
    }

    private fun prefixForMount(route: ArmeriaRoute): String =
        when {
            route.pathType == PathType.GLOB && route.path.endsWith("/**") -> route.path.removeSuffix("/**")
            else -> route.path
        }

    private fun isPrefixMount(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.SERVICE_UNDER -> true
            RouteMatch.ANNOTATED_SERVICE -> route.annotatedServiceHasPathPrefix
            else -> route.pathType == PathType.PREFIX ||
                (route.pathType == PathType.GLOB && route.path.endsWith("/**"))
        }

    private fun pathIsUnder(path: String, prefix: String): Boolean {
        val normalizedPath = ArmeriaRouteSupport.normalizePath(path)
        val normalizedPrefix = normalizePrefixMount(prefix)
        if (normalizedPrefix == "/") {
            return true
        }
        if (normalizedPath == normalizedPrefix || normalizedPath.removeSuffix("/") == normalizedPrefix) {
            return true
        }
        return normalizedPath.startsWith("$normalizedPrefix/")
    }

    private fun normalizePrefixMount(prefix: String): String {
        val normalized = ArmeriaRouteSupport.normalizePath(prefix)
        return if (normalized == "/") "/" else normalized.removeSuffix("/")
    }

    private fun httpMethodsOverlap(first: ArmeriaRoute, second: ArmeriaRoute): Boolean {
        if (matchesAllHttpMethods(first) || matchesAllHttpMethods(second)) {
            return true
        }
        val firstMethods = parseHttpMethods(first.httpMethod)
        val secondMethods = parseHttpMethods(second.httpMethod)
        if (firstMethods.isEmpty() || secondMethods.isEmpty()) {
            return first.httpMethod.equals(second.httpMethod, ignoreCase = true)
        }
        return firstMethods.any { firstMethod ->
            secondMethods.any { secondMethod -> firstMethod.equals(secondMethod, ignoreCase = true) }
        }
    }

    private fun parseHttpMethods(httpMethod: String): Set<String> =
        httpMethod.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun matchesAllHttpMethods(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.ANNOTATED_SERVICE -> true
            RouteMatch.ROUTE_FLUENT -> route.httpMethod.isBlank()
            RouteMatch.ANNOTATED_HTTP, RouteMatch.NON_HTTP, RouteMatch.RUNTIME,
            RouteMatch.DELEGATED_SPRING_MVC, RouteMatch.DELEGATED_SERVLET,
            RouteMatch.FILE_SERVICE, RouteMatch.HEALTH_CHECK,
            RouteMatch.VIRTUAL_HOST, RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> false
        }

    private fun buildHitsByVirtualFile(groups: List<DuplicateRegistrationGroup>): Map<VirtualFile, List<DuplicateRegistrationHit>> {
        val hitsByVirtualFile = mutableMapOf<VirtualFile, MutableList<DuplicateRegistrationHit>>()
        for (group in groups) {
            val seenElements = mutableSetOf<PsiElement>()
            for (route in group.routes) {
                val element = route.pointer.element ?: continue
                if (!seenElements.add(element)) {
                    continue
                }
                val virtualFile = element.containingFile?.virtualFile ?: continue
                val conflictingRoutes = buildConflictingRoutes(route, group.routes)
                hitsByVirtualFile.getOrPut(virtualFile) { mutableListOf() }.add(
                    DuplicateRegistrationHit(
                        pointer = route.pointer,
                        registrationLabel = registrationLabel(route),
                        registrationCount = conflictingRoutes.size + 1,
                        conflictingRoutes = conflictingRoutes,
                    ),
                )
            }
        }
        return hitsByVirtualFile
    }

    internal fun registrationLabel(route: ArmeriaRoute): String =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP, RouteMatch.RUNTIME, RouteMatch.HEALTH_CHECK,
            RouteMatch.DELEGATED_SPRING_MVC, RouteMatch.DELEGATED_SERVLET,
            -> "${route.httpMethod} ${route.path}"
            else -> route.path
        }

    private fun buildConflictingRoutes(current: ArmeriaRoute, groupRoutes: List<ArmeriaRoute>): List<ConflictingRouteRegistration> {
        val currentElement = current.pointer.element
        val conflicts = groupRoutes.filter { route ->
            val element = route.pointer.element
            element != null &&
                element !== currentElement &&
                routesOverlap(current, route) &&
                httpMethodsOverlap(current, route)
        }.distinctBy { it.pointer.element }
        val labelCounts = conflicts.groupingBy(::registrationLabel).eachCount()
        return conflicts.map { route ->
            val baseLabel = registrationLabel(route)
            ConflictingRouteRegistration(
                pointer = route.pointer,
                navigationLabel = disambiguatedNavigationLabel(route, baseLabel, labelCounts.getValue(baseLabel)),
            )
        }
    }

    private fun disambiguatedNavigationLabel(route: ArmeriaRoute, baseLabel: String, labelCount: Int): String {
        if (labelCount <= 1) {
            return baseLabel
        }
        val sourceHint = route.compactNavigationSourceHint()
        return if (sourceHint.isNotEmpty()) "$baseLabel ($sourceHint)" else baseLabel
    }

    private fun ArmeriaRoute.compactNavigationSourceHint(): String {
        val element = pointer.element ?: return ""
        val containingFile = element.containingFile ?: return ""
        val virtualFile = containingFile.virtualFile ?: return ""
        val document = PsiDocumentManager.getInstance(element.project).getDocument(containingFile)
            ?: return virtualFile.name
        val line = document.getLineNumber(element.textRange.startOffset) + 1
        return "${virtualFile.name}:$line"
    }
}

data class DuplicateRegistrationGroup(
    val routes: List<ArmeriaRoute>,
)

data class ConflictingRouteRegistration(
    val pointer: SmartPsiElementPointer<PsiElement>,
    val navigationLabel: String,
)

data class DuplicateRegistrationHit(
    val pointer: SmartPsiElementPointer<PsiElement>,
    val registrationLabel: String,
    val registrationCount: Int,
    val conflictingRoutes: List<ConflictingRouteRegistration>,
)

private data class DuplicateRegistrationIndex(
    val groups: List<DuplicateRegistrationGroup>,
    val hitsByVirtualFile: Map<VirtualFile, List<DuplicateRegistrationHit>>,
)
