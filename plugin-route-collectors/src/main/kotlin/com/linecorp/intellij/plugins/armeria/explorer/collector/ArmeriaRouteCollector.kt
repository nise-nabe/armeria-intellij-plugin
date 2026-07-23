package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.ArmeriaBuilderCallHeuristics
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.java.ArmeriaExtendedRegistrationCollector
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin.ArmeriaKotlinExtendedRegistrationCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.CoreServiceRegistrationSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Public route-collection façade for core annotated/service-registration collectors.
 *
 * Spring MVC/Boot and GraphQL/gRPC/Thrift contributors are supplied explicitly via [contributors]
 * (production callers use `ArmeriaRouteAnalysisCollector` in `plugin-route-analysis`).
 */
object ArmeriaRouteCollector {
    private val cacheKeys = ConcurrentHashMap<String, Key<CachedValue<List<ArmeriaRoute>>>>()

    fun collect(
        project: Project,
        includeProtoRoutes: Boolean = false,
        contributors: List<RouteContributor> = emptyList(),
    ): List<ArmeriaRoute> {
        val metrics = ArmeriaRouteCollectionMetrics()
        val startedAt = System.nanoTime()
        val routes =
            ArmeriaRouteCollectionMetrics.runWith(metrics) {
                val cachedRoutes =
                    CachedValuesManager.getManager(project).getCachedValue(
                        project,
                        cacheKey(contributors),
                        CachedValueProvider { computeProjectRoutes(project, contributors) },
                        false,
                    )
                if (includeProtoRoutes) {
                    mergeProtoRoutesIfEnabled(project, cachedRoutes, contributors)
                } else {
                    cachedRoutes
                }
            }
        metrics.elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        ArmeriaRouteCollectionMetrics.logIfEnabled(metrics.snapshot())
        return routes
    }

    private fun cacheKey(contributors: List<RouteContributor>): Key<CachedValue<List<ArmeriaRoute>>> {
        val id =
            contributors
                .map { it.javaClass.name }
                .sorted()
                .joinToString(",")
                .ifEmpty { "core-only" }
        return cacheKeys.getOrPut(id) { Key.create("armeria.route.collector.$id") }
    }

    private fun computeProjectRoutes(
        project: Project,
        contributors: List<RouteContributor>,
    ): CachedValueProvider.Result<List<ArmeriaRoute>> {
        val scope = collectionScope(project)
        val routes = mutableListOf<ArmeriaRoute>()
        val fallbackScannedFiles = linkedSetOf<VirtualFile>()
        val seenServiceRegistrations = mutableSetOf<String>()
        val seenConfigRoutes = mutableSetOf<String>()
        val psiFacade = JavaPsiFacade.getInstance(project)
        val serverBuilderOnClasspath = psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) != null

        ArmeriaRouteCollectorAnnotatedRoutes.collectAnnotatedRoutesIndexed(project, scope, routes)
        ArmeriaRouteCollectorServiceRegistration.collectServiceRegistrationsIndexed(
            project,
            scope,
            routes,
            seenServiceRegistrations,
        )
        if (!serverBuilderOnClasspath) {
            ArmeriaRouteCollectorServiceRegistration.collectServiceRegistrationsFallback(
                project,
                scope,
                routes,
                fallbackScannedFiles,
                seenServiceRegistrations,
            )
        }

        val context =
            buildCollectContext(
                project = project,
                scope = scope,
                routes = routes,
                seenServiceRegistrations = seenServiceRegistrations,
                seenConfigRoutes = seenConfigRoutes,
                fallbackScannedFiles = fallbackScannedFiles,
            )

        if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteCollector.collectServiceRegistrationsFallback(
                project,
                scope,
                routes,
                fallbackScannedFiles,
                seenServiceRegistrations,
            )
        }

        ArmeriaScalaRouteCollector.collectServiceRegistrationsFallback(
            project,
            scope,
            routes,
            fallbackScannedFiles,
            seenServiceRegistrations,
        )

        collectExtendedRegistrations(project, scope, routes, seenServiceRegistrations, fallbackScannedFiles)

        for (contributor in contributors) {
            contributor.collect(context)
        }

        return CachedValueProvider.Result.create(
            routes.sortedWith(
                compareBy(ArmeriaRoute::moduleName, ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target),
            ),
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }

    private fun buildCollectContext(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
        seenConfigRoutes: MutableSet<String>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
    ): RouteCollectContext =
        RouteCollectContext(
            project = project,
            scope = scope,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
            seenConfigRoutes = seenConfigRoutes,
            fallbackScannedFiles = fallbackScannedFiles,
            registration = CoreRegistration,
        )

    /**
     * Overlays proto (gRPC) routes on top of already-cached base routes by invoking
     * [RouteContributor.collectProtoOverlay] on each contributor.
     */
    private fun mergeProtoRoutesIfEnabled(
        project: Project,
        baseRoutes: List<ArmeriaRoute>,
        contributors: List<RouteContributor>,
    ): List<ArmeriaRoute> {
        val scope = collectionScope(project)
        val routes = baseRoutes.toMutableList()
        val context =
            buildCollectContext(
                project = project,
                scope = scope,
                routes = routes,
                seenServiceRegistrations = mutableSetOf(),
                seenConfigRoutes = mutableSetOf(),
                fallbackScannedFiles = mutableSetOf(),
            )
        for (contributor in contributors) {
            contributor.collectProtoOverlay(context)
        }
        return routes.sortedWith(
            compareBy(ArmeriaRoute::moduleName, ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target),
        )
    }

    private fun collectExtendedRegistrations(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        alreadyScannedFiles: Set<VirtualFile>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)) {
            if (virtualFile in alreadyScannedFiles) {
                continue
            }
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile ?: continue
            if (!referencesArmeriaJavaContent(psiFile)) {
                continue
            }
            ArmeriaExtendedRegistrationCollector.collectFromJavaFile(psiFile, routes, seenRegistrations)
        }
        if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
            for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
                if (virtualFile in alreadyScannedFiles) {
                    continue
                }
                val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
                if (!ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(ktFile)) {
                    continue
                }
                ArmeriaKotlinExtendedRegistrationCollector.collectFromFile(ktFile, routes, seenRegistrations)
            }
        }
    }

    private fun collectionScope(project: Project): GlobalSearchScope = GlobalSearchScope.projectScope(project)

    fun referencesArmeriaJavaContent(file: PsiJavaFile): Boolean = ArmeriaRouteSupport.referencesArmeriaJavaContent(file)

    fun looksLikeArmeriaBuilderCall(expression: PsiMethodCallExpression): Boolean =
        ArmeriaBuilderCallHeuristics.looksLikeJavaBuilderCall(expression)

    private object CoreRegistration : CoreServiceRegistrationSupport {
        override fun collectServiceRegistrationsInScope(
            element: PsiElement,
            routes: MutableList<ArmeriaRoute>,
            seen: MutableSet<String>,
        ) {
            if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
                ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(element, routes, seen)
            }
        }

        override fun collectServiceRegistrationFromMethodCall(
            expression: PsiMethodCallExpression,
            routes: MutableList<ArmeriaRoute>,
            seen: MutableSet<String>,
        ) {
            ArmeriaRouteCollectorServiceRegistration.collectServiceRegistrationFromMethodCall(
                expression,
                routes,
                seen,
            )
        }

        override fun referencesArmeriaKotlinContent(file: KtFile): Boolean =
            ArmeriaKotlinPluginSupport.isKotlinPluginAvailable() &&
                ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(file)
    }
}
