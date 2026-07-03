package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
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
 * Includes annotated HTTP methods and ServerBuilder `.service`, `.serviceUnder`, and
 * `.annotatedService` registrations. Excludes non-HTTP protocols such as gRPC.
 *
 * Cross-registration conflicts are detected, for example `@Get("/foo")` versus
 * `.service("/foo", …)`. Java in-class annotated duplicate HTTP routes are excluded because
 * [com.linecorp.intellij.plugins.armeria.inspection.ArmeriaDuplicateRouteInspection] covers them.
 */
internal object ArmeriaRouteDuplicateIndex {
    private val CHECKED_MATCHES = setOf(
        RouteMatch.ANNOTATED_HTTP,
        RouteMatch.ANNOTATED_SERVICE,
        RouteMatch.SERVICE,
        RouteMatch.SERVICE_UNDER,
    )

    fun duplicateHitsInFile(project: Project, file: PsiFile): List<DuplicateRegistrationHit> {
        val virtualFile = file.virtualFile ?: return emptyList()
        return getIndex(project).hitsByVirtualFile[virtualFile].orEmpty()
    }

    internal fun duplicateGroups(project: Project): List<DuplicateRegistrationGroup> {
        return getIndex(project).groups
    }

    internal fun findDuplicateGroups(routes: List<ArmeriaRoute>): List<DuplicateRegistrationGroup> {
        val groups = mutableListOf<DuplicateRegistrationGroup>()
        for ((_, moduleRoutes) in routes.filter { it.routeMatch in CHECKED_MATCHES }.groupBy { it.moduleName }) {
            if (moduleRoutes.size < 2) {
                continue
            }
            for (component in findConnectedComponents(moduleRoutes)) {
                if (component.size < 2 || isJavaInClassAnnotatedHttpOnly(component)) {
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

    private fun isJavaInClassAnnotatedHttpOnly(routes: List<ArmeriaRoute>): Boolean {
        if (routes.any { it.routeMatch != RouteMatch.ANNOTATED_HTTP }) {
            return false
        }
        var containingClass: PsiClass? = null
        for (route in routes) {
            val method = route.pointer.element as? PsiMethod ?: return false
            if (!method.language.isKindOf(JavaLanguage.INSTANCE)) {
                return false
            }
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
        pathsOverlap(first, second)

    private fun pathsOverlap(first: ArmeriaRoute, second: ArmeriaRoute): Boolean {
        val firstIsPrefixMount = isPrefixMount(first)
        val secondIsPrefixMount = isPrefixMount(second)
        return when {
            firstIsPrefixMount && secondIsPrefixMount ->
                pathIsUnder(first.path, second.path) || pathIsUnder(second.path, first.path)
            firstIsPrefixMount -> pathIsUnder(second.path, first.path)
            secondIsPrefixMount -> pathIsUnder(first.path, second.path)
            else -> first.path == second.path
        }
    }

    private fun isPrefixMount(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.SERVICE_UNDER -> true
            RouteMatch.ANNOTATED_SERVICE -> route.annotatedServiceHasPathPrefix
            else -> false
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
        return first.httpMethod.equals(second.httpMethod, ignoreCase = true)
    }

    private fun matchesAllHttpMethods(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.ANNOTATED_SERVICE -> true
            RouteMatch.ANNOTATED_HTTP, RouteMatch.NON_HTTP -> false
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
                hitsByVirtualFile.getOrPut(virtualFile) { mutableListOf() }.add(
                    DuplicateRegistrationHit(
                        pointer = route.pointer,
                        registrationLabel = registrationLabel(route),
                        registrationCount = overlappingRouteCount(route, group.routes),
                    ),
                )
            }
        }
        return hitsByVirtualFile
    }

    private fun overlappingRouteCount(route: ArmeriaRoute, routes: List<ArmeriaRoute>): Int =
        routes.count { routesOverlap(route, it) && httpMethodsOverlap(route, it) }

    private fun registrationLabel(route: ArmeriaRoute): String =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> "${route.httpMethod} ${route.path}"
            else -> route.path
        }
}

internal data class DuplicateRegistrationGroup(
    val routes: List<ArmeriaRoute>,
)

internal data class DuplicateRegistrationHit(
    val pointer: SmartPsiElementPointer<PsiElement>,
    val registrationLabel: String,
    val registrationCount: Int,
)

private data class DuplicateRegistrationIndex(
    val groups: List<DuplicateRegistrationGroup>,
    val hitsByVirtualFile: Map<VirtualFile, List<DuplicateRegistrationHit>>,
)
