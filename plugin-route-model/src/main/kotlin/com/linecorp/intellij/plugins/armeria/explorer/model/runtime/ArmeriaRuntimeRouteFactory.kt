package com.linecorp.intellij.plugins.armeria.explorer.model.runtime

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
        project: Project,
        delegationKind: DelegationKind? = null,
        exampleRequests: List<String> = emptyList(),
        exampleHeaders: List<String> = emptyList(),
    ): ArmeriaRoute =
        ArmeriaRoute.createRuntime(
            httpMethod = httpMethod,
            path = path,
            target = target,
            moduleName = moduleName,
            protocol = protocol,
            delegationKind = delegationKind,
            pointer = ArmeriaRuntimeRoutePointer(project),
            exampleRequests = exampleRequests,
            exampleHeaders = exampleHeaders,
        )
}
