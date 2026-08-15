package com.linecorp.intellij.plugins.armeria.client

import com.intellij.microservices.endpoints.EndpointsFilter
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.EndpointsUrlTargetProvider
import com.intellij.microservices.endpoints.ExternalEndpointsFilter
import com.intellij.microservices.endpoints.FrameworkPresentation
import com.intellij.microservices.endpoints.HTTP_CLIENT_TYPE
import com.intellij.microservices.endpoints.SearchScopeEndpointsFilter
import com.intellij.microservices.endpoints.presentation.HttpMethodPresentation
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCacheSupport
import com.linecorp.intellij.plugins.armeria.message

private const val QUERY_TAG = "Armeria-HTTP-Client"

class ArmeriaClientEndpointsProvider : EndpointsUrlTargetProvider<ArmeriaClientEndpointGroup, ArmeriaClientEndpoint> {
    override val endpointType = HTTP_CLIENT_TYPE

    override val presentation: FrameworkPresentation =
        FrameworkPresentation(
            QUERY_TAG,
            message("endpoints.framework.client.title"),
            ArmeriaIcons.Armeria,
        )

    override fun getStatus(project: Project): EndpointsProvider.Status {
        if (DumbService.isDumb(project)) {
            return EndpointsProvider.Status.AVAILABLE
        }
        return try {
            if (isArmeriaClientOnClasspath(project)) {
                EndpointsProvider.Status.AVAILABLE
            } else {
                EndpointsProvider.Status.UNAVAILABLE
            }
        } catch (_: IndexNotReadyException) {
            EndpointsProvider.Status.AVAILABLE
        }
    }

    override fun getEndpointGroups(
        project: Project,
        filter: EndpointsFilter,
    ): Iterable<ArmeriaClientEndpointGroup> {
        if (filter is ExternalEndpointsFilter) {
            return emptyList()
        }
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        return try {
            visibleClients(project, filter)
                .groupBy { ArmeriaClientEndpointsSupport.groupKey(it) }
                .map { (sourceKey, endpoints) ->
                    ArmeriaClientEndpointGroup(
                        moduleName = endpoints.first().moduleName,
                        sourceKey = sourceKey,
                        endpoints = endpoints,
                    )
                }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    override fun getEndpoints(group: ArmeriaClientEndpointGroup): Iterable<ArmeriaClientEndpoint> = group.endpoints

    override fun isValidEndpoint(
        group: ArmeriaClientEndpointGroup,
        endpoint: ArmeriaClientEndpoint,
    ): Boolean {
        val element = endpoint.pointer.element
        return element != null && element.isValid
    }

    override fun getEndpointPresentation(
        group: ArmeriaClientEndpointGroup,
        endpoint: ArmeriaClientEndpoint,
    ): ItemPresentation {
        val methods = ArmeriaClientEndpointsSupport.httpMethods(endpoint)
        val icon = ArmeriaIcons.Armeria
        return if (methods.size <= 1) {
            HttpMethodPresentation(endpoint.uri, methods.singleOrNull(), endpoint.target, icon)
        } else {
            HttpMethodPresentation(endpoint.uri, methods, endpoint.target, icon)
        }
    }

    override fun getModificationTracker(project: Project): ModificationTracker = ArmeriaRouteCacheSupport.modificationTracker(project)

    override fun getDocumentationElement(
        group: ArmeriaClientEndpointGroup,
        endpoint: ArmeriaClientEndpoint,
    ): PsiElement? = endpoint.pointer.element

    override fun getNavigationElement(
        group: ArmeriaClientEndpointGroup,
        endpoint: ArmeriaClientEndpoint,
    ): PsiElement? = endpoint.pointer.element

    override fun uiDataSnapshot(
        sink: DataSink,
        group: ArmeriaClientEndpointGroup,
        endpoint: ArmeriaClientEndpoint,
    ) {
        endpoint.pointer.element?.let { sink[CommonDataKeys.PSI_ELEMENT] = it }
        sink[EndpointsProvider.URL_TARGET_INFO] = getUrlTargetInfo(group, endpoint)
    }

    override fun getUrlTargetInfo(
        group: ArmeriaClientEndpointGroup,
        endpoint: ArmeriaClientEndpoint,
    ): Iterable<UrlTargetInfo> = listOf(ArmeriaClientEndpointsSupport.urlTargetInfo(endpoint))

    private fun visibleClients(
        project: Project,
        filter: EndpointsFilter,
    ): List<ArmeriaClientEndpoint> {
        val clients =
            ArmeriaClientCollector
                .collect(project)
                .filter(ArmeriaClientEndpointsSupport::isVisible)
        return when (filter) {
            is SearchScopeEndpointsFilter ->
                clients.filter { client ->
                    val virtualFile =
                        client.pointer.virtualFile
                            ?: client.pointer.element
                                ?.containingFile
                                ?.virtualFile
                    virtualFile != null && filter.transitiveSearchScope.contains(virtualFile)
                }
            else -> clients
        }
    }

    private fun isArmeriaClientOnClasspath(project: Project): Boolean {
        val scope = GlobalSearchScope.allScope(project)
        return JavaPsiFacade.getInstance(project).findClass(ArmeriaClientSupport.WEB_CLIENT_CLASS, scope) != null
    }
}
