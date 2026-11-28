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
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceDebugFormUrl
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.navigation.ArmeriaRouteNavigation
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeBuilder
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.pluginUnloadDisposable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArmeriaRouteExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true),
    Disposable,
    UiDataProvider {
    private val routeState = ArmeriaRouteExplorerRouteState()
    private var refreshGeneration = 0
    private var selectedRoute: ArmeriaRoute? = null
    private var currentModuleOnly = false
    private var initialRefreshScheduled = false
    private var pendingRouteSelection: ArmeriaRoute? = null

    @Volatile
    private var disposed = false

    private val searchField =
        JBTextField().apply {
            emptyText.text = message("route.explorer.filter.search")
        }
    private val methodFilter = JComboBox(methodFilterChoices())
    private val protocolFilter = JComboBox(protocolFilterChoices())
    private val sourceFilter = JComboBox(sourceFilterChoices())

    private val routeTree =
        Tree().apply {
            isRootVisible = false
            showsRootHandles = true
            emptyText.text = message("route.explorer.empty")
        }
    private val statusLabel = JBLabel()
    private val routeDetailPanel =
        ArmeriaRouteDetailPanel(
            project,
            routesProvider = { allRoutes() },
            staticRoutesProvider = { routeState.staticRoutes },
            lastSyncedDocsBaseUrlProvider = { routeState.lastDocServiceBaseUrl },
        )
    private val detailFootnote =
        JBLabel(message("route.explorer.footnote.static")).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

    init {
        val jumpToSourceAction =
            object : DumbAwareAction(message("route.explorer.action.jumpToSource")) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedRouteFromTree() != null
                }

                override fun actionPerformed(e: AnActionEvent) {
                    navigateToSelection()
                }
            }
        val copyPathAction =
            object : DumbAwareAction(message("route.explorer.action.copyPath")) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedRouteFromTree() != null
                }

                override fun actionPerformed(e: AnActionEvent) {
                    val route = selectedRouteFromTree() ?: return
                    CopyPasteManager.getInstance().setContents(StringSelection(route.path))
                }
            }
        val copyCurlAction =
            object : DumbAwareAction(message("route.explorer.action.copyCurl")) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = selectedRouteFromTree()?.let(::curlText) != null
                }

                override fun actionPerformed(e: AnActionEvent) {
                    val command = selectedRouteFromTree()?.let(::curlText) ?: return
                    CopyPasteManager.getInstance().setContents(StringSelection(command))
                }
            }
        val generateHttpRequestAction =
            ArmeriaGenerateHttpRequestAction(
                selectedRouteProvider = { selectedRouteFromTree() },
                routesProvider = { moduleFilteredRoutes(allRoutes()) },
                lastSyncedDocsBaseUrlProvider = { routeState.lastDocServiceBaseUrl },
            )
        val generateTestMethodAction =
            ArmeriaGenerateTestMethodAction(selectedRouteProvider = { selectedRouteFromTree() })
        val gotoMatchingClientAction =
            ArmeriaGotoMatchingClientAction(
                selectedRouteProvider = { selectedRouteFromTree() },
                parentDisposable = this@ArmeriaRouteExplorerPanel,
            )
        val openDebugFormAction =
            ArmeriaOpenDocServiceDebugFormAction(
                selectedRouteProvider = { selectedRouteFromTree() },
                routesProvider = { moduleFilteredRoutes(allRoutes()) },
                lastSyncedBaseUrlProvider = { routeState.lastDocServiceBaseUrl },
            )
        val routeActionsGroup =
            DefaultActionGroup(message("route.explorer.action.group.route"), true).apply {
                add(generateHttpRequestAction)
                add(generateTestMethodAction)
                add(gotoMatchingClientAction)
                add(openDebugFormAction)
                addSeparator()
                add(ArmeriaExportOpenApiAction { moduleFilteredRoutes(allRoutes()) })
            }
        val actionGroup =
            DefaultActionGroup().apply {
                add(
                    object : DumbAwareAction(message("route.explorer.action.refresh")) {
                        override fun actionPerformed(e: AnActionEvent) {
                            refresh()
                        }
                    },
                )
                add(
                    object : ToggleAction(message("route.explorer.filter.currentModule")) {
                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                        override fun isSelected(e: AnActionEvent): Boolean = currentModuleOnly

                        override fun setSelected(
                            e: AnActionEvent,
                            state: Boolean,
                        ) {
                            currentModuleOnly = state
                            rebuildTree()
                            updateStatusLabel()
                        }
                    },
                )
                add(ArmeriaSyncRuntimeRoutesAction())
                add(
                    ArmeriaOpenDocServiceAction(
                        routesProvider = { moduleFilteredRoutes(allRoutes()) },
                        lastSyncedBaseUrlProvider = { routeState.lastDocServiceBaseUrl },
                    ),
                )
                add(routeActionsGroup)
            }
        val popupActionGroup =
            DefaultActionGroup().apply {
                add(jumpToSourceAction)
                add(copyPathAction)
                add(copyCurlAction)
                addSeparator()
                add(generateHttpRequestAction)
                add(generateTestMethodAction)
                add(gotoMatchingClientAction)
                add(openDebugFormAction)
            }
        toolbar =
            ActionManager
                .getInstance()
                .createActionToolbar("ArmeriaRouteExplorer", actionGroup, true)
                .also {
                    it.targetComponent = this
                }.component

        searchField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = filtersChanged()

                override fun removeUpdate(e: DocumentEvent) = filtersChanged()

                override fun changedUpdate(e: DocumentEvent) = filtersChanged()
            },
        )
        for (filter in listOf(methodFilter, protocolFilter, sourceFilter)) {
            filter.addActionListener { filtersChanged() }
        }
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    if (currentModuleOnly) {
                        rebuildTree()
                        updateStatusLabel()
                    }
                }
            },
        )

        val popupMenu =
            ActionManager
                .getInstance()
                .createActionPopupMenu("ArmeriaRouteExplorerPopup", popupActionGroup)
        popupMenu.setTargetComponent(routeTree)
        routeTree.componentPopupMenu = popupMenu.component

        routeTree.cellRenderer = ArmeriaRouteExplorerTreeRenderer()
        TreeUIHelper.getInstance().installTreeSpeedSearch(
            routeTree,
            { path: javax.swing.tree.TreePath ->
                val node = path.lastPathComponent
                when (val userObject = (node as? javax.swing.tree.DefaultMutableTreeNode)?.userObject) {
                    is ArmeriaRouteTreeBuilder.RouteNode,
                    is ArmeriaRouteTreeBuilder.ModuleNode,
                    is ArmeriaRouteTreeBuilder.VirtualHostNode,
                    is ArmeriaRouteTreeBuilder.DiscoveryNode,
                    -> ArmeriaRouteTreeBuilder.speedSearchText(userObject)

                    else -> ""
                }
            },
            true,
        )
        routeTree.addTreeSelectionListener { _: TreeSelectionEvent ->
            selectedRoute = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
            routeDetailPanel.setRoute(selectedRoute)
        }
        routeTree.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2) {
                        navigateToSelection()
                    }
                }
            },
        )
        routeTree.registerKeyboardAction(
            { navigateToSelection() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JTree.WHEN_FOCUSED,
        )

        val detailPanel =
            JPanel(BorderLayout()).apply {
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

        val treeScrollPane =
            JBScrollPane(routeTree).apply {
                minimumSize = Dimension(JBUI.scale(300), 0)
            }
        val splitter =
            OnePixelSplitter(true, 0.55f).apply {
                firstComponent = treeScrollPane
                secondComponent = detailPanel
                setHonorComponentsMinimumSize(false)
            }

        val filterRow =
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
                add(methodFilter)
                add(protocolFilter)
                add(sourceFilter)
            }
        val filterPanel =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(4, 0)
                add(searchField, BorderLayout.CENTER)
                add(filterRow, BorderLayout.SOUTH)
            }
        val headerPanel =
            JPanel(BorderLayout()).apply {
                add(statusLabel, BorderLayout.NORTH)
                add(filterPanel, BorderLayout.CENTER)
            }
        val contentPanel =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 8, 0, 8)
                add(headerPanel, BorderLayout.NORTH)
                add(splitter, BorderLayout.CENTER)
            }

        fun updateSplitterOrientation() {
            val horizontal = contentPanel.width >= JBUI.scale(700)
            val vertical = !horizontal
            if (splitter.orientation != vertical) {
                splitter.orientation = vertical
                splitter.proportion = if (horizontal) 0.7f else 0.55f
            }
        }
        contentPanel.addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    updateSplitterOrientation()
                }
            },
        )
        updateSplitterOrientation()
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
        ReadAction
            .nonBlocking<List<ArmeriaRoute>> {
                ArmeriaRouteAnalysisCollector.collect(project, includeProtoRoutes = true)
            }.inSmartMode(project)
            .expireWhen { disposed || pluginUnloadDisposable().isDisposed }
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { collectedRoutes ->
                if (generation != refreshGeneration) {
                    return@finishOnUiThread
                }
                routeState.applyStatic(collectedRoutes)
                rebuildTree()
                updateStatusLabel()
                updateDetailFootnote()
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun selectRoute(route: ArmeriaRoute): Boolean {
        if (applyRouteSelection(route)) {
            pendingRouteSelection = null
            return true
        }
        pendingRouteSelection = route
        return false
    }

    fun staticRoutes(): List<ArmeriaRoute> = routeState.staticRoutes

    fun applyRuntimeRoutes(
        routes: List<ArmeriaRoute>,
        specificationUrl: String? = null,
    ) {
        val docsBaseUrl = specificationUrl?.let(ArmeriaDocServiceDebugFormUrl::docsBaseUrlFromSpecificationUrl)
        routeState.applyRuntime(routes, docsBaseUrl)
        rebuildTree()
        updateStatusLabel()
        updateDetailFootnote()
    }

    private fun allRoutes(): List<ArmeriaRoute> = routeState.allRoutes()

    private fun rebuildTree() {
        val pending = pendingRouteSelection
        val previousSelection = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
        val visibleRoutes = filterRoutes(allRoutes())
        val root = ArmeriaRouteTreeBuilder.buildRoot(visibleRoutes)
        routeTree.model = DefaultTreeModel(root)
        if (visibleRoutes.isEmpty()) {
            selectedRoute = null
            routeDetailPanel.clear()
            return
        }
        pendingRouteSelection = null
        val preferred = pending ?: previousSelection
        if (preferred != null && applyRouteSelection(preferred)) {
            return
        }
        if (pending != null && previousSelection != null && applyRouteSelection(previousSelection)) {
            return
        }
        val initialRoute =
            visibleRoutes.firstOrNull { it.routeMatch != RouteMatch.LISTEN_PORT }
                ?: visibleRoutes.first()
        if (!applyRouteSelection(initialRoute)) {
            selectedRoute = null
            routeDetailPanel.setRoute(null)
        }
    }

    private fun applyRouteSelection(route: ArmeriaRoute): Boolean {
        val root = (routeTree.model as? DefaultTreeModel)?.root as? DefaultMutableTreeNode ?: return false
        val restored = ArmeriaRouteExplorerFiltering.restoreTreeSelection(routeTree, root, route)
        if (!restored) {
            return false
        }
        selectedRoute = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)
        routeDetailPanel.setRoute(selectedRoute)
        routeTree.selectionPath?.let(routeTree::scrollPathToVisible)
        return true
    }

    private fun updateStatusLabel() {
        val collectedRoutes = allRoutes()
        val summary =
            if (
                currentModuleOnly &&
                ArmeriaRouteExplorerFiltering.selectedEditorModule(project) == null &&
                collectedRoutes.isNotEmpty()
            ) {
                message("route.explorer.summary.moduleFilterNoEditor")
            } else {
                ArmeriaRouteExplorerFiltering.summary(filterRoutes(collectedRoutes))
            }
        val syncedUrl = routeState.lastDocServiceBaseUrl
        statusLabel.text =
            if (syncedUrl.isNullOrBlank() || routeState.runtimeRoutes.isEmpty()) {
                summary
            } else {
                "$summary · ${message("route.explorer.summary.synced", syncedUrl)}"
            }
    }

    private fun updateDetailFootnote() {
        val runtimeCount = routeState.runtimeRoutes.size
        detailFootnote.text =
            if (runtimeCount == 0) {
                message("route.explorer.footnote.static")
            } else {
                message("route.explorer.footnote.runtime", runtimeCount)
            }
    }

    private fun moduleFilteredRoutes(routes: List<ArmeriaRoute>): List<ArmeriaRoute> =
        ArmeriaRouteExplorerFiltering.filterRoutes(project, routes, currentModuleOnly)

    private fun filterRoutes(routes: List<ArmeriaRoute>): List<ArmeriaRoute> {
        var filtered = moduleFilteredRoutes(routes)
        val query = searchField.text.trim().lowercase()
        if (query.isNotEmpty()) {
            filtered = filtered.filter { it.speedSearchText.lowercase().contains(query) }
        }
        val method = (methodFilter.selectedItem as? FilterChoice)?.value
        if (!method.isNullOrEmpty()) {
            filtered = filtered.filter { matchesMethodFilter(it, method) }
        }
        val protocol = (protocolFilter.selectedItem as? FilterChoice)?.value
        if (!protocol.isNullOrEmpty()) {
            filtered = filtered.filter { matchesProtocolFilter(it, protocol) }
        }
        when ((sourceFilter.selectedItem as? FilterChoice)?.value) {
            SOURCE_STATIC -> filtered = filtered.filter { it.routeMatch != RouteMatch.RUNTIME }
            SOURCE_RUNTIME -> filtered = filtered.filter { it.routeMatch == RouteMatch.RUNTIME }
        }
        return filtered
    }

    private fun matchesMethodFilter(
        route: ArmeriaRoute,
        method: String,
    ): Boolean {
        if (route.httpMethod.equals(method, ignoreCase = true)) {
            return true
        }
        if (route.httpMethod.isNotBlank()) {
            return false
        }
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_SERVICE,
            RouteMatch.SERVICE,
            RouteMatch.SERVICE_UNDER,
            RouteMatch.ROUTE_FLUENT,
            RouteMatch.DELEGATED,
            -> true

            RouteMatch.FILE_SERVICE,
            RouteMatch.HEALTH_CHECK,
            -> method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)

            else -> false
        }
    }

    private fun matchesProtocolFilter(
        route: ArmeriaRoute,
        protocol: String,
    ): Boolean =
        when (protocol) {
            PROTOCOL_HTTP -> route.protocolType == RouteProtocol.HTTP || route.protocol.equals("HTTP", ignoreCase = true)
            PROTOCOL_GRPC -> route.protocolType == RouteProtocol.GRPC || route.protocol.equals("gRPC", ignoreCase = true)
            PROTOCOL_GRAPHQL -> route.protocolType == RouteProtocol.GRAPHQL || route.protocol.equals("GraphQL", ignoreCase = true)
            PROTOCOL_THRIFT -> route.protocolType == RouteProtocol.THRIFT || route.protocol.equals("Thrift", ignoreCase = true)
            PROTOCOL_DOC_SERVICE -> route.isDocService || route.protocolType == RouteProtocol.DOC_SERVICE
            else -> true
        }

    private fun filtersChanged() {
        rebuildTree()
        updateStatusLabel()
    }

    private fun curlText(route: ArmeriaRoute): String? =
        ArmeriaRouteCurlSupport.curlText(
            route = route,
            staticRoutes = routeState.staticRoutes,
            lastSyncedDocsBaseUrl = routeState.lastDocServiceBaseUrl,
        )

    private fun navigateToSelection() {
        val route = selectedRouteFromTree() ?: return
        ArmeriaRouteNavigation.navigateToRoute(project, route, parentDisposable = this)
    }

    private fun selectedRouteFromTree(): ArmeriaRoute? = ArmeriaRouteTreeBuilder.selectedRoute(routeTree.lastSelectedPathComponent)

    override fun uiDataSnapshot(sink: DataSink) {
        selectedRoute?.let { sink[ArmeriaRouteDataKeys.SELECTED_ROUTE] = it }
    }

    override fun dispose() {
        disposed = true
    }
}

private const val SOURCE_STATIC = "static"
private const val SOURCE_RUNTIME = "runtime"
private const val PROTOCOL_HTTP = "http"
private const val PROTOCOL_GRPC = "grpc"
private const val PROTOCOL_GRAPHQL = "graphql"
private const val PROTOCOL_THRIFT = "thrift"
private const val PROTOCOL_DOC_SERVICE = "docservice"

private data class FilterChoice(
    val label: String,
    val value: String? = null,
) {
    override fun toString(): String = label
}

private fun methodFilterChoices(): Array<FilterChoice> =
    arrayOf(
        FilterChoice(message("route.explorer.filter.allMethods")),
        FilterChoice("GET", "GET"),
        FilterChoice("POST", "POST"),
        FilterChoice("PUT", "PUT"),
        FilterChoice("PATCH", "PATCH"),
        FilterChoice("DELETE", "DELETE"),
        FilterChoice("OPTIONS", "OPTIONS"),
        FilterChoice("HEAD", "HEAD"),
    )

private fun protocolFilterChoices(): Array<FilterChoice> =
    arrayOf(
        FilterChoice(message("route.explorer.filter.allProtocols")),
        FilterChoice("HTTP", PROTOCOL_HTTP),
        FilterChoice("gRPC", PROTOCOL_GRPC),
        FilterChoice("GraphQL", PROTOCOL_GRAPHQL),
        FilterChoice("Thrift", PROTOCOL_THRIFT),
        FilterChoice("DocService", PROTOCOL_DOC_SERVICE),
    )

private fun sourceFilterChoices(): Array<FilterChoice> =
    arrayOf(
        FilterChoice(message("route.explorer.filter.allSources")),
        FilterChoice(message("route.explorer.filter.static"), SOURCE_STATIC),
        FilterChoice(message("route.explorer.filter.runtime"), SOURCE_RUNTIME),
    )
