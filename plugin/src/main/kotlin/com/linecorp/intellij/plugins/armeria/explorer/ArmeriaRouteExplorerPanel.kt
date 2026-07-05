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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleTextAttributes
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
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ArmeriaRouteExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable, UiDataProvider {
    private var staticRoutes: List<ArmeriaRoute> = emptyList()
    private var runtimeRoutes: List<ArmeriaRoute> = emptyList()
    private var refreshGeneration = 0
    private var runtimeApplyGeneration = 0
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
        }
        toolbar = ActionManager.getInstance().createActionToolbar("ArmeriaRouteExplorer", actionGroup, true).also {
            it.targetComponent = this
        }.component

        routeTree.cellRenderer = RouteTreeCellRenderer()
        TreeUIHelper.getInstance().installTreeSpeedSearch(
            routeTree,
            { path: TreePath ->
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
                if (runtimeApplyGeneration < generation) {
                    runtimeRoutes = emptyList()
                }
                rebuildTree()
                updateStatusLabel()
                updateDetailFootnote()
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun staticRoutes(): List<ArmeriaRoute> = staticRoutes

    fun applyRuntimeRoutes(routes: List<ArmeriaRoute>) {
        runtimeApplyGeneration = refreshGeneration
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
        val selectionRestored = previousSelection != null && restoreTreeSelection(root, previousSelection)
        when {
            visibleRoutes.isEmpty() -> routeDetailPanel.clear()
            selectionRestored -> {
                routeDetailPanel.setRoute(ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent))
            }
            else -> routeDetailPanel.setRoute(null)
        }
    }

    private fun updateStatusLabel() {
        val collectedRoutes = allRoutes()
        statusLabel.text = if (
            currentModuleOnly &&
            selectedEditorModule() == null &&
            collectedRoutes.isNotEmpty()
        ) {
            message("route.explorer.summary.moduleFilterNoEditor")
        } else {
            summary(filterRoutes(collectedRoutes))
        }
    }

    private fun updateDetailFootnote() {
        detailFootnote.text = if (runtimeRoutes.isEmpty()) {
            message("route.explorer.footnote.static")
        } else {
            message("route.explorer.footnote.runtime", runtimeRoutes.size)
        }
    }

    private fun restoreTreeSelection(root: DefaultMutableTreeNode, route: ArmeriaRoute): Boolean {
        for (moduleIndex in 0 until root.childCount) {
            val moduleNode = root.getChildAt(moduleIndex) as DefaultMutableTreeNode
            val module = moduleNode.userObject as? ArmeriaRouteTreeBuilder.ModuleNode ?: continue
            if (module.name != route.moduleName) {
                continue
            }
            for (routeIndex in 0 until moduleNode.childCount) {
                val routeNode = moduleNode.getChildAt(routeIndex) as DefaultMutableTreeNode
                val visibleRoute = (routeNode.userObject as? ArmeriaRouteTreeBuilder.RouteNode)?.route ?: continue
                if (routesMatch(visibleRoute, route)) {
                    routeTree.selectionPath = TreePath(routeNode.path)
                    return true
                }
            }
        }
        return false
    }

    private fun routesMatch(left: ArmeriaRoute, right: ArmeriaRoute): Boolean {
        return left.moduleName == right.moduleName &&
            left.path == right.path &&
            left.target == right.target &&
            left.routeMatch == right.routeMatch &&
            left.httpMethod == right.httpMethod
    }

    private fun filterRoutes(routes: List<ArmeriaRoute>): List<ArmeriaRoute> {
        val withoutRuntime = routes.filter { it.routeMatch != RouteMatch.RUNTIME }
        val runtimeOnly = routes.filter { it.routeMatch == RouteMatch.RUNTIME }
        if (!currentModuleOnly) {
            return withoutRuntime + runtimeOnly
        }
        val selectedModule = selectedEditorModule() ?: return runtimeOnly
        return withoutRuntime.filter { it.moduleName == selectedModule.name } + runtimeOnly
    }

    private fun selectedEditorModule(): com.intellij.openapi.module.Module? {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null
        return ModuleUtilCore.findModuleForFile(file, project)
    }

    private fun summary(collectedRoutes: List<ArmeriaRoute>): String {
        if (collectedRoutes.isEmpty()) {
            return message("route.explorer.summary.empty")
        }
        val docServiceDetected = collectedRoutes.any { it.isDocService }
        val runtimeCount = collectedRoutes.count { it.routeMatch == RouteMatch.RUNTIME }
        return buildString {
            append(message("route.explorer.summary.routes", collectedRoutes.size))
            append(" · ")
            append(
                collectedRoutes.groupBy { it.protocol }.entries.joinToString {
                    message("route.explorer.summary.protocolBreakdown", it.key, it.value.size)
                },
            )
            if (docServiceDetected) {
                append(" · ")
                append(message("route.explorer.summary.docService"))
            }
            if (runtimeCount > 0) {
                append(" · ")
                append(message("route.explorer.summary.runtime", runtimeCount))
            }
        }
    }

    private fun navigateToSelection() {
        val route = selectedRouteFromTree() ?: return
        ReadAction.nonBlocking<Navigatable?> {
            val element = route.pointer.element as? Navigatable
            element?.takeIf { it.canNavigate() }
        }
            .inSmartMode(project)
            .expireWith(this)
            .finishOnUiThread(ModalityState.any()) { navigatable ->
                navigatable?.navigate(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun selectedRouteFromTree(): ArmeriaRoute? {
        return ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
    }

    override fun uiDataSnapshot(sink: DataSink) {
        selectedRoute?.let { sink[ArmeriaRouteDataKeys.SELECTED_ROUTE] = it }
    }

    override fun dispose() = Unit

    private inner class RouteTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            toolTipText = null
            val node = value as? javax.swing.tree.DefaultMutableTreeNode ?: return
            when (val userObject = node.userObject) {
                is ArmeriaRouteTreeBuilder.ModuleNode -> {
                    append(message("route.explorer.tree.module", userObject.name, userObject.routeCount), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
                is ArmeriaRouteTreeBuilder.RouteNode -> renderRoute(userObject.route)
            }
        }

        private fun renderRoute(route: ArmeriaRoute) {
            val pillLabel = ArmeriaHttpMethodPill.pillLabel(route)
            toolTipText = buildString {
                append(route.methodLabel)
                append(' ')
                append(route.path)
                append(" → ")
                append(route.shortTarget)
            }
            append(ArmeriaHttpMethodPill.pillText(pillLabel), ArmeriaHttpMethodPill.textAttributes(route))
            append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(route.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }
}
