package com.linecorp.intellij.plugins.armeria.explorer.model

import com.intellij.openapi.project.Project

interface ArmeriaTestMethodGeneration {
    fun supports(route: ArmeriaRoute): Boolean

    fun insertFromRouteExplorer(
        project: Project,
        route: ArmeriaRoute,
    ): Boolean
}
