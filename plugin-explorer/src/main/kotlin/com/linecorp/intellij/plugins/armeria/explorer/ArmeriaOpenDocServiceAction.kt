package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaOpenDocServiceAction(
    private val routesProvider: () -> List<ArmeriaRoute>,
    private val lastSyncedBaseUrlProvider: () -> String? = { null },
) : DumbAwareAction(
        message("route.explorer.action.openDocService"),
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            e.presentation.description = null
            return
        }
        val routes = routesProvider()
        val offersPortChoice =
            lastSyncedBaseUrlProvider().isNullOrBlank() &&
                ArmeriaDocServiceSupport.listenPortBindings(routes).size > 1
        if (offersPortChoice) {
            e.presentation.isEnabled = ArmeriaDocServiceSupport.hasDocService(routes)
            e.presentation.description = message("route.explorer.action.openDocService.description")
            return
        }
        val url = resolveUrl()
        e.presentation.isEnabled = url != null
        e.presentation.description =
            if (url != null) {
                message("route.explorer.action.openDocService.descriptionWithUrl", url)
            } else {
                message("route.explorer.action.openDocService.description")
            }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val routes = routesProvider()
        if (!lastSyncedBaseUrlProvider().isNullOrBlank()) {
            val url = resolveUrl() ?: return
            BrowserUtil.browse(url)
            return
        }
        val bindings = ArmeriaDocServiceSupport.listenPortBindings(routes)
        if (bindings.size <= 1) {
            val url = resolveUrl() ?: return
            BrowserUtil.browse(url)
            return
        }
        JBPopupFactory
            .getInstance()
            .createPopupChooserBuilder(bindings)
            .setTitle(message("route.explorer.action.openDocService.choosePort"))
            .setItemChosenCallback { binding ->
                ArmeriaDocServiceSupport
                    .primaryUrl(
                        routes = routes,
                        baseUrl = ArmeriaDocServiceSupport.serverBaseUrl(binding),
                    )?.let(BrowserUtil::browse)
            }.setRenderer(
                object : javax.swing.DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: javax.swing.JList<*>,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean,
                    ): java.awt.Component {
                        val label =
                            if (value is Pair<*, *>) {
                                val port = value.first as? Int
                                val https = value.second as? Boolean
                                if (port != null && https != null) {
                                    ArmeriaDocServiceSupport.serverBaseUrl(port to https)
                                } else {
                                    value.toString()
                                }
                            } else {
                                value?.toString().orEmpty()
                            }
                        return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
                    }
                },
            ).createPopup()
            .showInFocusCenter()
    }

    private fun resolveUrl(): String? =
        ArmeriaDocServiceSupport.docsBaseUrl(
            routes = routesProvider(),
            lastSyncedBaseUrl = lastSyncedBaseUrlProvider(),
        )
}
