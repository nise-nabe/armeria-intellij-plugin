package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultTreeModel

class ArmeriaRouteExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable, UiDataProvider {
    private var staticRoutes: List<ArmeriaRoute> = emptyList()
    private var runtimeRoutes: List<ArmeriaRoute> = emptyList()
    private var refreshGeneration = 0
    private var selectedRoute: ArmeriaRoute? = null
    private var currentModuleOnly = false
    private var initialRefreshScheduled = false

    private val routeTree = Tree().apply {
        isRootVisible = false
        showsRootHandles = true
        emptyText.text = message("route.explorer.empty")
    }
    private val statusLabel = JBLabel()
    private val routeDetailPanel = ArmeriaRouteDetailPanel()
    private val detailFootnote = JBLabel(message("route.explorer.footnote.static")).apply {
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    init {
        val actionGroup = DefaultActionGroup().apply {
            add(object : DumbAwareAction(message("route.explorer.action.refresh")) {
                override fun actionPerformed(e: AnActionEvent) {
                    refresh()
                }
            })
            add(object : ToggleAction(message("route.explorer.filter.currentModule")) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun isSelected(e: AnActionEvent): Boolean = currentModuleOnly

                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    currentModuleOnly = state
                    rebuildTree()
                    updateStatusLabel()
                }
            })
            add(ArmeriaGenerateHttpRequestAction { selectedRouteFromTree() })
            add(ArmeriaSyncRuntimeRoutesAction())
            add(ArmeriaOpenDocServiceAction { filterRoutes(allRoutes()) })
        }
        toolbar = ActionManager.getInstance().createActionToolbar("ArmeriaRouteExplorer", actionGroup, true).also {
            it.targetComponent = this
        }.component

        routeTree.cellRenderer = ArmeriaRouteExplorerTreeRenderer()
        TreeUIHelper.getInstance().installTreeSpeedSearch(
            routeTree,
            { path: javax.swing.tree.TreePath ->
                val node = path.lastPathComponent
                when (val userObject = (node as? javax.swing.tree.DefaultMutableTreeNode)?.userObject) {
                    is ArmeriaRouteTreeBuilder.RouteNode -> userObject.route.speedSearchText
                    is ArmeriaRouteTreeBuilder.ModuleNode -> userObject.name
                    else -> ""
                }
            },
            true,
        )
        routeTree.addTreeSelectionListener { _: TreeSelectionEvent ->
            selectedRoute = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
            routeDetailPanel.setRoute(selectedRoute)
        }
        routeTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    navigateToSelection()
                }
            }
        })
        routeTree.registerKeyboardAction(
            { navigateToSelection() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JTree.WHEN_FOCUSED,
        )

        val detailPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(
                JBScrollPane(
                    routeDetailPanel,
                    JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                ),
                BorderLayout.CENTER,
            )
            add(detailFootnote, BorderLayout.SOUTH)
        }

        val treeScrollPane = JBScrollPane(routeTree).apply {
            minimumSize = Dimension(JBUI.scale(300), 0)
        }
        val splitter = OnePixelSplitter(false, 0.7f).apply {
            firstComponent = treeScrollPane
            secondComponent = detailPanel
            setHonorComponentsMinimumSize(false)
        }

        val contentPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 0, 8)
            add(statusLabel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }
        setContent(contentPanel)

        routeDetailPanel.clear()
        statusLabel.text = message("route.explorer.summary.notLoaded")
    }

    fun scheduleInitialRefreshIfNeeded() {
        if (initialRefreshScheduled) {
            return
        }
        initialRefreshScheduled = true
        refresh()
    }

    fun refresh() {
        val generation = ++refreshGeneration
        statusLabel.text = message("route.explorer.summary.refreshing")
        ReadAction.nonBlocking<List<ArmeriaRoute>> {
            ArmeriaRouteCollector.collect(project, includeProtoRoutes = true)
        }
            .inSmartMode(project)
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { collectedRoutes ->
                if (generation != refreshGeneration) {
                    return@finishOnUiThread
                }
                staticRoutes = collectedRoutes
                // Keep DocService-synced runtime routes across static refreshes until the next sync.
                rebuildTree()
                updateStatusLabel()
                updateDetailFootnote()
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun staticRoutes(): List<ArmeriaRoute> = staticRoutes

    fun applyRuntimeRoutes(routes: List<ArmeriaRoute>) {
        runtimeRoutes = routes
        rebuildTree()
        updateStatusLabel()
        updateDetailFootnote()
    }

    private fun allRoutes(): List<ArmeriaRoute> = staticRoutes + runtimeRoutes

    private fun rebuildTree() {
        val previousSelection = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
        val visibleRoutes = filterRoutes(allRoutes())
        val root = ArmeriaRouteTreeBuilder.buildRoot(visibleRoutes)
        routeTree.model = DefaultTreeModel(root)
        val selectionRestored = previousSelection != null &&
            ArmeriaRouteExplorerFiltering.restoreTreeSelection(routeTree, root, previousSelection)
        when {
            visibleRoutes.isEmpty() -> {
                selectedRoute = null
                routeDetailPanel.clear()
            }
            selectionRestored -> {
                selectedRoute = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
                routeDetailPanel.setRoute(selectedRoute)
            }
            else -> {
                selectedRoute = null
                routeDetailPanel.setRoute(null)
            }
        }
    }

    private fun updateStatusLabel() {
        val collectedRoutes = allRoutes()
        statusLabel.text = if (
            currentModuleOnly &&
            ArmeriaRouteExplorerFiltering.selectedEditorModule(project) == null &&
            collectedRoutes.isNotEmpty()
        ) {
            message("route.explorer.summary.moduleFilterNoEditor")
        } else {
            ArmeriaRouteExplorerFiltering.summary(filterRoutes(collectedRoutes))
        }
    }

    private fun updateDetailFootnote() {
        detailFootnote.text = if (runtimeRoutes.isEmpty()) {
            message("route.explorer.footnote.static")
        } else {
            message("route.explorer.footnote.runtime", runtimeRoutes.size)
        }
    }

    private fun filterRoutes(routes: List<ArmeriaRoute>): List<ArmeriaRoute> =
        ArmeriaRouteExplorerFiltering.filterRoutes(project, routes, currentModuleOnly)

    private fun navigateToSelection() {
        val route = selectedRouteFromTree() ?: return
        ArmeriaRouteNavigation.navigateToPointer(project, route.pointer, parentDisposable = this)
    }

    private fun selectedRouteFromTree(): ArmeriaRoute? {
        return ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
    }

    override fun uiDataSnapshot(sink: DataSink) {
        selectedRoute?.let { sink[ArmeriaRouteDataKeys.SELECTED_ROUTE] = it }
    }

    override fun dispose() = Unit
}
