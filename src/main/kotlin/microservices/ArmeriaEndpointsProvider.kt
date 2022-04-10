package com.linecorp.intellij.plugins.armeria.microservices

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.microservices.endpoints.EndpointType
import com.intellij.microservices.endpoints.EndpointsFilter
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.FrameworkPresentation
import com.intellij.microservices.endpoints.HTTP_SERVER_TYPE
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.microservices.model.ArmeriaEndpointModel
import com.linecorp.intellij.plugins.armeria.microservices.model.ArmeriaServiceModel

@Suppress("UnstableApiUsage")
abstract class ArmeriaEndpointsProvider: EndpointsProvider<ArmeriaServiceModel, ArmeriaEndpointModel> {
    override val endpointType: EndpointType get() = HTTP_SERVER_TYPE
    override val presentation: FrameworkPresentation
        get() = FrameworkPresentation("Armeria", "Armeria API", ArmeriaIcons.Armeria)

    override fun getStatus(project: Project): EndpointsProvider.Status {
        val files = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        return if (files.isEmpty()) {
            EndpointsProvider.Status.AVAILABLE
        } else {
            EndpointsProvider.Status.HAS_ENDPOINTS
        }
    }

    override fun getModificationTracker(project: Project): ModificationTracker {
        return PsiManager.getInstance(project).modificationTracker.forLanguage(JavaLanguage.INSTANCE)
    }

    override fun getEndpointGroups(project: Project, filter: EndpointsFilter): Iterable<ArmeriaServiceModel> {
        return when (filter) {
            is SearchScopeEndpointsFilter -> {
                listOf(ArmeriaServiceModel)
            }
            else -> {
                listOf()
            }
        }
    }

    override fun getEndpoints(group: ArmeriaServiceModel): Iterable<ArmeriaEndpointModel> {
        TODO("Not yet implemented")
    }

    override fun getEndpointData(group: ArmeriaServiceModel, endpoint: ArmeriaEndpointModel, dataId: String): Any? {
        TODO("Not yet implemented")
    }

    override fun getEndpointPresentation(group: ArmeriaServiceModel, endpoint: ArmeriaEndpointModel): ItemPresentation {
        TODO("Not yet implemented")
    }

    override fun isValidEndpoint(group: ArmeriaServiceModel, endpoint: ArmeriaEndpointModel): Boolean {
        TODO("Not yet implemented")
    }

}
