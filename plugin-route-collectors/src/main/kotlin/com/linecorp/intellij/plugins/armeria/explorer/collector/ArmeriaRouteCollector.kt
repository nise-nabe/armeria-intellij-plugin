package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
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
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributorRegistry
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteRegistrationCallbacks
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

/**
 * Public route-collection façade. Aggregates core annotated/service-registration collectors
 * with Spring MVC/Boot contributors and GraphQL/gRPC/Thrift contributors registered via
 * [RouteContributorRegistry]. In production, contributors are registered by
 * `ArmeriaRouteContributorBootstrap` (in `plugin-route-analysis`). In tests, contributors
 * can be registered explicitly after [RouteContributorRegistry.clearForTests].
 */
object ArmeriaRouteCollector {
    fun collect(
        project: Project,
        includeProtoRoutes: Boolean = false,
    ): List<ArmeriaRoute> {
        val metrics = ArmeriaRouteCollectionMetrics()
        val startedAt = System.nanoTime()
        val routes =
            ArmeriaRouteCollectionMetrics.runWith(metrics) {
                val cachedRoutes =
                    CachedValuesManager.getManager(project).getCachedValue(project) {
                        computeProjectRoutes(project)
                    }
                if (includeProtoRoutes) {
                    mergeProtoRoutesIfEnabled(project, cachedRoutes)
                } else {
                    cachedRoutes
                }
            }
        metrics.elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        ArmeriaRouteCollectionMetrics.logIfEnabled(metrics.snapshot())
        return routes
    }

    private fun computeProjectRoutes(project: Project): CachedValueProvider.Result<List<ArmeriaRoute>> {
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

        collectExtendedRegistrations(project, scope, routes, seenServiceRegistrations, fallbackScannedFiles)

        for (contributor in RouteContributorRegistry.all()) {
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
        includeProtoRoutes: Boolean = false,
    ): RouteCollectContext {
        val callbacks =
            RouteRegistrationCallbacks(
                collectServiceRegistrationsInScope = { element, routeList, seen ->
                    if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable()) {
                        ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(element, routeList, seen)
                    }
                },
                collectServiceRegistrationFromMethodCall = { expression, routeList, seen ->
                    ArmeriaRouteCollectorServiceRegistration.collectServiceRegistrationFromMethodCall(
                        expression,
                        routeList,
                        seen,
                    )
                },
                referencesArmeriaKotlinContent = { file ->
                    ArmeriaKotlinPluginSupport.isKotlinPluginAvailable() &&
                        (file as? KtFile)?.let {
                            ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(it)
                        } == true
                },
            )
        return RouteCollectContext(
            project = project,
            scope = scope,
            routes = routes,
            seenServiceRegistrations = seenServiceRegistrations,
            seenConfigRoutes = seenConfigRoutes,
            fallbackScannedFiles = fallbackScannedFiles,
            registration = callbacks,
            includeProtoRoutes = includeProtoRoutes,
        )
    }

    /**
     * Overlays proto (gRPC) routes on top of already-cached base routes. Contributors with
     * [RouteCollectContext.includeProtoRoutes]=true are responsible for skipping non-proto
     * collection and adding only gRPC routes.
     */
    private fun mergeProtoRoutesIfEnabled(
        project: Project,
        baseRoutes: List<ArmeriaRoute>,
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
                includeProtoRoutes = true,
            )
        for (contributor in RouteContributorRegistry.all()) {
            contributor.collect(context)
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
}
