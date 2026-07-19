package com.linecorp.intellij.plugins.armeria.explorer.navigation

import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind

object ArmeriaRuntimeRouteFactory {
    fun createRuntime(
        httpMethod: String,
        path: String,
        target: String,
        moduleName: String,
        protocol: String,
        project: Project? = null,
        delegationKind: DelegationKind? = null,
    ): ArmeriaRoute =
        ArmeriaRoute.createRuntime(
            httpMethod = httpMethod,
            path = path,
            target = target,
            moduleName = moduleName,
            protocol = protocol,
            delegationKind = delegationKind,
            pointer = project?.let(::ArmeriaRuntimeRoutePointer) ?: ArmeriaRuntimeRoutePointer.withoutProject(),
        )
}
