package com.linecorp.intellij.plugins.armeria.explorer.endpoints

import com.intellij.microservices.endpoints.EndpointsFilter
import com.intellij.microservices.endpoints.EndpointsProvider
import com.intellij.microservices.endpoints.EndpointsUrlTargetProvider
import com.intellij.microservices.endpoints.ExternalEndpointsFilter
import com.intellij.microservices.endpoints.FrameworkPresentation
import com.intellij.microservices.endpoints.HTTP_SERVER_TYPE
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
import com.intellij.psi.util.PsiModificationTracker
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

private const val QUERY_TAG = "Armeria"

class ArmeriaEndpointsProvider : EndpointsUrlTargetProvider<ArmeriaEndpointGroup, ArmeriaRoute> {
    override val endpointType = HTTP_SERVER_TYPE

    override val presentation: FrameworkPresentation =
        FrameworkPresentation(
            QUERY_TAG,
            message("endpoints.framework.title"),
            ArmeriaIcons.Armeria,
        )

    override fun getStatus(project: Project): EndpointsProvider.Status {
        if (DumbService.isDumb(project)) {
            return EndpointsProvider.Status.AVAILABLE
        }
        return try {
            if (isArmeriaOnClasspath(project)) {
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
    ): Iterable<ArmeriaEndpointGroup> {
        if (filter is ExternalEndpointsFilter) {
            return emptyList()
        }
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        return try {
            visibleRoutes(project, filter)
                .groupBy { ArmeriaEndpointsSupport.groupKey(it) }
                .map { (sourceKey, routes) ->
                    ArmeriaEndpointGroup(
                        moduleName = routes.first().moduleName,
                        sourceKey = sourceKey,
                        routes = routes,
                    )
                }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    override fun getEndpoints(group: ArmeriaEndpointGroup): Iterable<ArmeriaRoute> = group.routes

    override fun isValidEndpoint(
        group: ArmeriaEndpointGroup,
        endpoint: ArmeriaRoute,
    ): Boolean {
        val element = endpoint.pointer.element
        return element != null && element.isValid
    }

    override fun getEndpointPresentation(
        group: ArmeriaEndpointGroup,
        endpoint: ArmeriaRoute,
    ): ItemPresentation {
        val methods = ArmeriaEndpointsSupport.httpMethods(endpoint)
        val icon = ArmeriaIcons.Armeria
        return if (methods.size <= 1) {
            HttpMethodPresentation(endpoint.path, methods.singleOrNull(), endpoint.shortTarget, icon)
        } else {
            HttpMethodPresentation(endpoint.path, methods, endpoint.shortTarget, icon)
        }
    }

    override fun getModificationTracker(project: Project): ModificationTracker = PsiModificationTracker.getInstance(project)

    override fun getDocumentationElement(
        group: ArmeriaEndpointGroup,
        endpoint: ArmeriaRoute,
    ): PsiElement? = endpoint.pointer.element

    override fun getNavigationElement(
        group: ArmeriaEndpointGroup,
        endpoint: ArmeriaRoute,
    ): PsiElement? = endpoint.pointer.element

    override fun uiDataSnapshot(
        sink: DataSink,
        group: ArmeriaEndpointGroup,
        endpoint: ArmeriaRoute,
    ) {
        endpoint.pointer.element?.let { sink[CommonDataKeys.PSI_ELEMENT] = it }
        sink[EndpointsProvider.URL_TARGET_INFO] = getUrlTargetInfo(group, endpoint)
    }

    override fun getUrlTargetInfo(
        group: ArmeriaEndpointGroup,
        endpoint: ArmeriaRoute,
    ): Iterable<UrlTargetInfo> = listOf(ArmeriaEndpointsSupport.urlTargetInfo(endpoint))

    private fun visibleRoutes(
        project: Project,
        filter: EndpointsFilter,
    ): List<ArmeriaRoute> {
        val routes =
            ArmeriaRouteAnalysisCollector
                .collect(project, includeProtoRoutes = true)
                .filter(ArmeriaEndpointsSupport::isVisibleServerRoute)
        return when (filter) {
            is SearchScopeEndpointsFilter ->
                routes.filter { route ->
                    val virtualFile =
                        route.pointer.virtualFile
                            ?: route.pointer.element
                                ?.containingFile
                                ?.virtualFile
                    virtualFile != null && filter.transitiveSearchScope.contains(virtualFile)
                }
            else -> routes
        }
    }

    private fun isArmeriaOnClasspath(project: Project): Boolean {
        val scope = GlobalSearchScope.allScope(project)
        val facade = JavaPsiFacade.getInstance(project)
        return facade.findClass(ArmeriaRouteSupport.SERVER_BUILDER_CLASS, scope) != null ||
            facade.findClass(ArmeriaRouteSupport.GET_ANNOTATION, scope) != null
    }
}
