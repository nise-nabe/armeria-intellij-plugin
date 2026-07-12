package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

object ArmeriaRouteCollector {

    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun collect(project: Project, includeProtoRoutes: Boolean = false): List<ArmeriaRoute> {
        val metrics = ArmeriaRouteCollectionMetrics()
        val startedAt = System.nanoTime()
        val routes = ArmeriaRouteCollectionMetrics.runWith(metrics) {
            val cachedRoutes = CachedValuesManager.getManager(project).getCachedValue(project) {
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
        val psiFacade = JavaPsiFacade.getInstance(project)
        val serverBuilderOnClasspath = psiFacade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) != null
        val springBootArmeriaAvailable = ArmeriaRouteSupport.isSpringBootArmeriaAvailable(psiFacade, scope)

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
        if (isKotlinPluginAvailable()) {
            ArmeriaKotlinRouteCollector.collectServiceRegistrationsFallback(
                project,
                scope,
                routes,
                fallbackScannedFiles,
                seenServiceRegistrations,
            )
            if (springBootArmeriaAvailable) {
                ArmeriaKotlinSpringBootRouteCollector.collect(
                    project,
                    scope,
                    routes,
                    fallbackScannedFiles,
                    seenServiceRegistrations,
                )
            }
        }
        if (springBootArmeriaAvailable) {
            ArmeriaSpringBootRouteCollector.collect(
                project,
                scope,
                routes,
                seenServiceRegistrations,
            )
        }
        ArmeriaGraphqlRouteCollector.collect(project, scope, routes)
        ArmeriaThriftRouteCollector.collect(project, scope, routes)
        collectExtendedRegistrations(project, scope, routes, seenServiceRegistrations, fallbackScannedFiles)

        return CachedValueProvider.Result.create(
            routes.sortedWith(
                compareBy(ArmeriaRoute::moduleName, ArmeriaRoute::path, ArmeriaRoute::httpMethod, ArmeriaRoute::target),
            ),
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }

    private fun mergeProtoRoutesIfEnabled(project: Project, baseRoutes: List<ArmeriaRoute>): List<ArmeriaRoute> {
        if (!ArmeriaGrpcRouteCollector.isProtoRouteDiscoveryEnabled()) {
            return baseRoutes
        }
        val scope = collectionScope(project)
        val routes = baseRoutes.toMutableList()
        ArmeriaGrpcRouteCollector.collect(project, scope, routes)
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
        if (isKotlinPluginAvailable()) {
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

    private fun collectionScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.projectScope(project)

    fun referencesArmeriaJavaContent(file: PsiJavaFile): Boolean {
        val hasArmeriaImports = file.importList
            ?.allImportStatements
            ?.any { statement ->
                statement.importReference?.qualifiedName?.startsWith(ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX) == true
            } ?: false
        if (hasArmeriaImports) {
            return true
        }
        return ArmeriaRouteSupport.referencesArmeriaInText(file.viewProvider.contents)
    }

    internal fun collectProgrammaticDecorators(element: PsiElement, registrationPath: String): List<String> {
        if (element is PsiMethodCallExpression) {
            return ArmeriaDecoratorSupport.collectProgrammaticDecorators(element, registrationPath)
        }
        if (isKotlinPluginAvailable()) {
            return ArmeriaKotlinDecoratorSupport.collectProgrammaticDecorators(element, registrationPath)
        }
        return emptyList()
    }

    internal fun collectBuilderTimeoutHints(element: PsiElement): List<String> {
        if (element is PsiMethodCallExpression) {
            return ArmeriaTimeoutSupport.collectBuilderTimeoutHints(element)
        }
        if (isKotlinPluginAvailable()) {
            return ArmeriaKotlinTimeoutSupport.collectBuilderTimeoutHints(element)
        }
        return emptyList()
    }

    private fun isKotlinPluginAvailable(): Boolean =
        PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
