package com.linecorp.intellij.plugins.armeria.run

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaSpringConfigRouteCollector

internal object ArmeriaRunLaunchInfo {
    private val LOG = logger<ArmeriaRunLaunchInfo>()

    fun resolve(
        project: Project,
        module: Module?,
        mainClassFqn: String?,
    ): ArmeriaRunServiceUrls {
        if (module == null || DumbService.isDumb(project)) {
            return ArmeriaRunServiceUrls()
        }
        return try {
            ReadAction.computeBlocking<ArmeriaRunServiceUrls, RuntimeException> {
                val moduleRoutes =
                    ArmeriaRouteAnalysisCollector
                        .collect(project)
                        .filter { it.moduleName == module.name }
                val programmatic =
                    ArmeriaServerListenPortSupport.extractFromMainClass(project, module, mainClassFqn)
                var combined = moduleRoutes
                if (programmatic == null && ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(combined) == null) {
                    combined = mergeRoutes(combined, collectSpringConfigRoutes(project, module))
                }
                val listen = programmatic ?: ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(combined)
                ArmeriaRunUrlBuilder.fromRoutes(listen, combined)
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: IndexNotReadyException) {
            ArmeriaRunServiceUrls()
        } catch (e: Exception) {
            LOG.warn("Failed to resolve Armeria run service URLs", e)
            ArmeriaRunServiceUrls()
        }
    }

    private fun collectSpringConfigRoutes(
        project: Project,
        module: Module,
    ): List<ArmeriaRoute> {
        val routes = mutableListOf<ArmeriaRoute>()
        val seen = mutableSetOf<String>()
        ArmeriaSpringConfigRouteCollector.collect(
            project,
            GlobalSearchScope.moduleScope(module),
            routes,
            seen,
        )
        if (routes.none { it.path.startsWith(":") }) {
            ArmeriaSpringConfigRouteCollector.collect(
                project,
                GlobalSearchScope.projectScope(project),
                routes,
                seen,
            )
        }
        return routes
    }

    private fun mergeRoutes(
        primary: List<ArmeriaRoute>,
        extra: List<ArmeriaRoute>,
    ): List<ArmeriaRoute> {
        if (extra.isEmpty()) {
            return primary
        }
        if (primary.isEmpty()) {
            return extra
        }
        return primary +
            extra.filter { extraRoute ->
                primary.none { it.path == extraRoute.path && it.routeMatch == extraRoute.routeMatch }
            }
    }
}
