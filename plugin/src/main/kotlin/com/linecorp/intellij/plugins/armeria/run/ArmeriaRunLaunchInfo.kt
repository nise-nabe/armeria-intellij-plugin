package com.linecorp.intellij.plugins.armeria.run

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector

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
                val routes =
                    ArmeriaRouteAnalysisCollector
                        .collect(project)
                        .filter { it.moduleName == module.name }
                val programmatic =
                    ArmeriaServerListenPortSupport.extractFromMainClass(project, module, mainClassFqn)
                val listen = programmatic ?: ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(routes)
                ArmeriaRunUrlBuilder.fromRoutes(listen, routes)
            }
        } catch (_: IndexNotReadyException) {
            ArmeriaRunServiceUrls()
        } catch (e: Exception) {
            LOG.warn("Failed to resolve Armeria run service URLs", e)
            ArmeriaRunServiceUrls()
        }
    }
}
