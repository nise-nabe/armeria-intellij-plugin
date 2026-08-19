package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.AppExecutorUtil
import com.linecorp.intellij.plugins.armeria.expireWithPluginUnload
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaExplorerAccess
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.navigation.ArmeriaRouteNavigation
import com.linecorp.intellij.plugins.armeria.message
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

internal object ArmeriaClientRouteNavigation {
    fun openMatchingRoutes(
        project: Project,
        endpoint: ArmeriaClientEndpoint,
        parentDisposable: Disposable? = null,
        mouseEvent: MouseEvent? = null,
    ) {
        ReadAction
            .nonBlocking<List<ArmeriaRoute>> {
                ArmeriaClientRouteLinkSupport.matchingRoutes(project, endpoint)
            }.inSmartMode(project)
            .expireWith(project)
            .expireWithPluginUnload()
            .coalesceBy(this, project, "openMatchingRoutes")
            .let { coordinator ->
                if (parentDisposable != null) coordinator.expireWith(parentDisposable) else coordinator
            }.finishOnUiThread(ModalityState.any()) { routes ->
                when {
                    routes.isEmpty() ->
                        showEmptyMatch(
                            project,
                            "client.explorer.matching.routes.empty",
                            "client.explorer.action.gotoRoute.popup",
                        )
                    routes.size == 1 -> openRoute(project, routes.single(), parentDisposable)
                    else -> showRouteChooser(project, routes, parentDisposable, mouseEvent)
                }
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun openMatchingClients(
        project: Project,
        route: ArmeriaRoute,
        parentDisposable: Disposable? = null,
        mouseEvent: MouseEvent? = null,
    ) {
        ReadAction
            .nonBlocking<List<ArmeriaClientEndpoint>> {
                ArmeriaClientRouteLinkSupport.matchingClients(project, route)
            }.inSmartMode(project)
            .expireWith(project)
            .expireWithPluginUnload()
            .coalesceBy(this, project, "openMatchingClients")
            .let { coordinator ->
                if (parentDisposable != null) coordinator.expireWith(parentDisposable) else coordinator
            }.finishOnUiThread(ModalityState.any()) { endpoints ->
                when {
                    endpoints.isEmpty() ->
                        showEmptyMatch(
                            project,
                            "client.explorer.matching.clients.empty",
                            "route.explorer.action.gotoClient.popup",
                        )
                    endpoints.size == 1 -> openClient(project, endpoints.single(), parentDisposable)
                    else -> showClientChooser(project, endpoints, parentDisposable, mouseEvent)
                }
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun endpointForCall(call: PsiElement): ArmeriaClientEndpoint? {
        if (call is PsiMethodCallExpression) {
            val endpoints = mutableListOf<ArmeriaClientEndpoint>()
            ArmeriaClientCollector.collectClientFromMethodCall(call, endpoints, mutableSetOf())
            return endpoints.firstOrNull()
        }
        return ArmeriaKotlinClientCollector.endpointForCall(call)
    }

    private fun openRoute(
        project: Project,
        route: ArmeriaRoute,
        parentDisposable: Disposable?,
    ) {
        ArmeriaExplorerAccess.ensureRoutePanel(project) { panel ->
            panel?.selectRoute(route)
        }
        ArmeriaRouteNavigation.navigateToRoute(project, route, parentDisposable)
    }

    private fun openClient(
        project: Project,
        endpoint: ArmeriaClientEndpoint,
        parentDisposable: Disposable?,
    ) {
        ArmeriaExplorerAccess.ensureClientPanel(project) { panel ->
            panel?.selectEndpoint(endpoint)
        }
        ArmeriaRouteNavigation.navigateToPointer(
            project,
            endpoint.pointer,
            endpoint.sourceOffset,
            parentDisposable,
        )
    }

    private fun showRouteChooser(
        project: Project,
        routes: List<ArmeriaRoute>,
        parentDisposable: Disposable?,
        mouseEvent: MouseEvent?,
    ) {
        val popup =
            JBPopupFactory
                .getInstance()
                .createPopupChooserBuilder(routes)
                .setTitle(message("client.explorer.action.gotoRoute.popup"))
                .setItemChosenCallback { route -> openRoute(project, route, parentDisposable) }
                .setRenderer(routeChooserRenderer())
                .createPopup()
        showPopup(popup, mouseEvent)
    }

    private fun showClientChooser(
        project: Project,
        endpoints: List<ArmeriaClientEndpoint>,
        parentDisposable: Disposable?,
        mouseEvent: MouseEvent?,
    ) {
        val popup =
            JBPopupFactory
                .getInstance()
                .createPopupChooserBuilder(endpoints)
                .setTitle(message("route.explorer.action.gotoClient.popup"))
                .setItemChosenCallback { endpoint -> openClient(project, endpoint, parentDisposable) }
                .setRenderer(clientChooserRenderer())
                .createPopup()
        showPopup(popup, mouseEvent)
    }

    private fun routeChooserRenderer(): DefaultListCellRenderer =
        object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label =
                    if (value is ArmeriaRoute) {
                        "${value.methodLabel} ${value.path} (${value.moduleName})"
                    } else {
                        value?.toString().orEmpty()
                    }
                val component = super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
                toolTipText = null
                return component
            }
        }

    private fun clientChooserRenderer(): DefaultListCellRenderer =
        object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label =
                    if (value is ArmeriaClientEndpoint) {
                        ArmeriaClientInvocationSupport.chooserLabel(value)
                    } else {
                        value?.toString().orEmpty()
                    }
                val component = super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
                toolTipText = null
                return component
            }
        }

    private fun showPopup(
        popup: com.intellij.openapi.ui.popup.JBPopup,
        mouseEvent: MouseEvent?,
    ) {
        if (mouseEvent != null) {
            popup.show(RelativePoint(mouseEvent))
        } else {
            popup.showInFocusCenter()
        }
    }

    private fun showEmptyMatch(
        project: Project,
        messageKey: String,
        titleKey: String,
    ) {
        if (project.isDisposed) {
            return
        }
        Messages.showInfoMessage(project, message(messageKey), message(titleKey))
    }
}
