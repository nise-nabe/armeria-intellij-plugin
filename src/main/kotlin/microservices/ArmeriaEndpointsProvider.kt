package com.linecorp.intellij.plugins.armeria.microservices

import com.intellij.microservices.endpoints.EndpointType
import com.intellij.microservices.endpoints.EndpointsFilter
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.FrameworkPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker

@Suppress("UnstableApiUsage")
class ArmeriaEndpointsProvider: EndpointsProvider<Any, Any> {
    override val endpointType: EndpointType
        get() = TODO("Not yet implemented")
    override val presentation: FrameworkPresentation
        get() = TODO("Not yet implemented")

    override fun getEndpointData(group: Any, endpoint: Any, dataId: String): Any? {
        TODO("Not yet implemented")
    }

    override fun getEndpointGroups(project: Project, filter: EndpointsFilter): Iterable<Any> {
        TODO("Not yet implemented")
    }

    override fun getEndpointPresentation(group: Any, endpoint: Any): ItemPresentation {
        TODO("Not yet implemented")
    }

    override fun getEndpoints(group: Any): Iterable<Any> {
        TODO("Not yet implemented")
    }

    override fun getModificationTracker(project: Project): ModificationTracker {
        TODO("Not yet implemented")
    }

    override fun getStatus(project: Project): EndpointsProvider.Status {
        TODO("Not yet implemented")
    }

    override fun isValidEndpoint(group: Any, endpoint: Any): Boolean {
        TODO("Not yet implemented")
    }
}
