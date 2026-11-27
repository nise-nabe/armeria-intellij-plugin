package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaTestMethodGeneration

class ArmeriaTestMethodGenerationServiceImpl : ArmeriaTestMethodGeneration {
    override fun supports(route: ArmeriaRoute): Boolean = ArmeriaTestMethodGenerator.supports(route)

    override fun insertFromRouteExplorer(
        project: Project,
        route: ArmeriaRoute,
    ): Boolean = ArmeriaTestMethodInserter.insertFromRouteExplorer(project, route)
}
