package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel

class ArmeriaRouteExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable {
    private val routes = DefaultListModel<ArmeriaRoute>()
    private var currentRoutes: List<ArmeriaRoute> = emptyList()
    private val routeList = JBList(routes)
    private val statusLabel = JBLabel()

    init {
        val actionGroup = DefaultActionGroup().apply {
            add(object : DumbAwareAction("Refresh") {
                override fun actionPerformed(e: AnActionEvent) {
                    refresh()
                }
            })
        }
        toolbar = ActionManager.getInstance().createActionToolbar("ArmeriaRouteExplorer", actionGroup, true).component

        routeList.emptyText.text = "No Armeria routes found"
        routeList.cellRenderer = object : ColoredListCellRenderer<ArmeriaRoute>() {
            override fun customizeCellRenderer(
                list: JList<out ArmeriaRoute>,
                value: ArmeriaRoute?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                if (value == null) {
                    return
                }
                append(value.presentation)
                append("  ")
                append(value.secondaryPresentation, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
        ListSpeedSearch(routeList) { it.presentation }
        ScrollingUtil.installActions(routeList)
        routeList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                statusLabel.text = routeList.selectedValue?.secondaryPresentation ?: summary()
            }
        }
        routeList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(event: java.awt.event.MouseEvent) {
                if (event.clickCount == 2) {
                    navigateToSelection()
                }
            }
        })
        routeList.registerKeyboardAction(
            { navigateToSelection() },
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
            javax.swing.JComponent.WHEN_FOCUSED,
        )

        val contentPanel = JPanel(VerticalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8)
            add(statusLabel)
            add(com.intellij.ui.components.JBScrollPane(routeList))
        }
        setContent(contentPanel)

        refresh()
    }

    fun refresh() {
        statusLabel.text = "Refreshing Armeria routes..."
        ReadAction.nonBlocking<List<ArmeriaRoute>> {
            ArmeriaRouteCollector.collect(project)
        }.finishOnUiThread(ModalityState.any()) { collectedRoutes ->
            currentRoutes = collectedRoutes
            routes.clear()
            collectedRoutes.forEach(routes::addElement)
            statusLabel.text = summary(collectedRoutes)
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun summary(collectedRoutes: List<ArmeriaRoute> = currentRoutes): String {
        if (collectedRoutes.isEmpty()) {
            return "No Armeria services or routes were discovered in the current project."
        }
        val docServiceDetected = collectedRoutes.any { it.protocol == "DocService" }
        return buildString {
            append("${collectedRoutes.size} route entries")
            append(" · ")
            append(collectedRoutes.groupBy { it.protocol }.entries.joinToString { "${it.key}: ${it.value.size}" })
            if (docServiceDetected) {
                append(" · DocService detected")
            }
        }
    }

    private fun navigateToSelection() {
        val element = routeList.selectedValue?.pointer?.element as? Navigatable ?: return
        if (element.canNavigate()) {
            element.navigate(true)
        }
    }

    override fun dispose() = Unit
}
