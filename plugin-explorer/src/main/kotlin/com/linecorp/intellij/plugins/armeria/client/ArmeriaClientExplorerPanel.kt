package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.expireWithPluginUnload
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaGenerateClientHttpRequestAction
import com.linecorp.intellij.plugins.armeria.explorer.navigation.ArmeriaRouteNavigation
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class ArmeriaClientExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true),
    Disposable {
    private var initialRefreshScheduled = false
    private var pendingEndpointSelection: ArmeriaClientEndpoint? = null
    private var collectedEndpoints: List<ArmeriaClientEndpoint> = emptyList()

    private val listModel = DefaultListModel<ClientListItem>()
    private val endpointList = JBList(listModel)
    private val statusLabel = JBLabel()
    private val searchField =
        JBTextField().apply {
            emptyText.text = message("client.explorer.filter.search")
        }
    private val clientDetailPanel =
        ArmeriaClientDetailPanel { endpoint ->
            ArmeriaClientRouteNavigation.openMatchingRoutes(
                project,
                endpoint,
                parentDisposable = this@ArmeriaClientExplorerPanel,
            )
        }

    init {
        val actionGroup =
            DefaultActionGroup().apply {
                add(
                    object : DumbAwareAction(message("client.explorer.action.refresh")) {
                        override fun actionPerformed(e: AnActionEvent) {
                            refresh()
                        }
                    },
                )
                add(
                    object : DumbAwareAction(message("client.explorer.action.gotoRoute")) {
                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                        override fun update(e: AnActionEvent) {
                            e.presentation.isEnabled = selectedEndpoint() != null
                        }

                        override fun actionPerformed(e: AnActionEvent) {
                            val endpoint = selectedEndpoint() ?: return
                            ArmeriaClientRouteNavigation.openMatchingRoutes(
                                project,
                                endpoint,
                                parentDisposable = this@ArmeriaClientExplorerPanel,
                            )
                        }
                    },
                )
                add(ArmeriaGenerateClientHttpRequestAction { selectedEndpoint() })
            }
        toolbar =
            ActionManager
                .getInstance()
                .createActionToolbar("ArmeriaClientExplorer", actionGroup, true)
                .also {
                    it.targetComponent = this
                }.component

        searchField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = refreshListPresentation()

                override fun removeUpdate(e: DocumentEvent) = refreshListPresentation()

                override fun changedUpdate(e: DocumentEvent) = refreshListPresentation()
            },
        )

        endpointList.emptyText.text = message("client.explorer.empty")
        endpointList.cellRenderer =
            object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ): Component {
                    val label =
                        when (value) {
                            is ClientListItem.Group -> value.label
                            is ClientListItem.Endpoint -> formatListLabel(value.endpoint)
                            else -> value?.toString()
                        }
                    val component = super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
                    toolTipText = null
                    font = list.font
                    border = JBUI.Borders.empty(2, 4)
                    when (value) {
                        is ClientListItem.Group -> {
                            font = list.font.deriveFont(java.awt.Font.BOLD)
                        }

                        is ClientListItem.Endpoint -> {
                            border = JBUI.Borders.empty(2, 16, 2, 4)
                            toolTipText = buildClientTooltip(value.endpoint)
                        }
                    }
                    return component
                }
            }
        endpointList.addListSelectionListener(
            ListSelectionListener { _: ListSelectionEvent ->
                clientDetailPanel.setEndpoint(selectedEndpoint())
            },
        )
        endpointList.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2) {
                        navigateToSelection()
                    }
                }
            },
        )
        endpointList.registerKeyboardAction(
            { navigateToSelection() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JBList.WHEN_FOCUSED,
        )

        val detailPanel =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8)
                add(
                    JBScrollPane(
                        clientDetailPanel,
                        JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
                    ),
                    BorderLayout.CENTER,
                )
            }

        val listScrollPane =
            JBScrollPane(endpointList).apply {
                minimumSize = Dimension(JBUI.scale(300), 0)
            }
        val splitter =
            OnePixelSplitter(true, 0.55f).apply {
                firstComponent = listScrollPane
                secondComponent = detailPanel
                setHonorComponentsMinimumSize(false)
            }

        val headerPanel =
            JPanel(BorderLayout()).apply {
                add(statusLabel, BorderLayout.NORTH)
                add(
                    JPanel(BorderLayout()).apply {
                        border = JBUI.Borders.empty(4, 0)
                        add(searchField, BorderLayout.CENTER)
                    },
                    BorderLayout.CENTER,
                )
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
                splitter.proportion = if (horizontal) 0.65f else 0.55f
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

        clientDetailPanel.clear()
        statusLabel.text = message("client.explorer.summary.notLoaded")
    }

    fun scheduleInitialRefreshIfNeeded() {
        if (initialRefreshScheduled) {
            return
        }
        initialRefreshScheduled = true
        refresh()
    }

    fun refresh() {
        statusLabel.text = message("client.explorer.summary.refreshing")
        ReadAction
            .nonBlocking<List<ArmeriaClientEndpoint>> {
                ArmeriaClientCollector.collect(project)
            }.inSmartMode(project)
            .expireWith(this)
            .expireWithPluginUnload()
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { endpoints ->
                collectedEndpoints = endpoints
                val pending = pendingEndpointSelection
                val previous = selectedEndpoint()
                listModel.removeAllElements()
                groupedItems(filterEndpoints(endpoints)).forEach(listModel::addElement)
                if (collectedEndpoints.isNotEmpty()) {
                    pendingEndpointSelection = null
                    val preferred = pending ?: previous
                    when {
                        preferred != null && selectEndpointNow(preferred) -> Unit
                        pending != null && previous != null -> selectEndpointNow(previous)
                        else -> firstVisibleEndpoint()?.let(::selectEndpointNow)
                    }
                }
                updateStatusLabel(endpoints)
                clientDetailPanel.setEndpoint(selectedEndpoint())
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun selectEndpoint(endpoint: ArmeriaClientEndpoint): Boolean {
        if (selectEndpointNow(endpoint)) {
            pendingEndpointSelection = null
            return true
        }
        pendingEndpointSelection = endpoint
        return false
    }

    private fun selectEndpointNow(endpoint: ArmeriaClientEndpoint): Boolean {
        for (index in 0 until listModel.size) {
            val candidate = (listModel.getElementAt(index) as? ClientListItem.Endpoint)?.endpoint ?: continue
            if (sameEndpoint(candidate, endpoint)) {
                endpointList.selectedIndex = index
                endpointList.ensureIndexIsVisible(index)
                return true
            }
        }
        return false
    }

    private fun sameEndpoint(
        left: ArmeriaClientEndpoint,
        right: ArmeriaClientEndpoint,
    ): Boolean {
        val leftIdentity = endpointIdentity(left)
        val rightIdentity = endpointIdentity(right)
        if (leftIdentity != null && rightIdentity != null) {
            return leftIdentity == rightIdentity
        }
        return left.clientType == right.clientType &&
            left.uri == right.uri &&
            left.target == right.target &&
            left.moduleName == right.moduleName
    }

    private fun selectedEndpoint(): ArmeriaClientEndpoint? = (endpointList.selectedValue as? ClientListItem.Endpoint)?.endpoint

    private fun firstVisibleEndpoint(): ArmeriaClientEndpoint? {
        for (index in 0 until listModel.size) {
            val endpoint = (listModel.getElementAt(index) as? ClientListItem.Endpoint)?.endpoint
            if (endpoint != null) {
                return endpoint
            }
        }
        return null
    }

    private fun filterEndpoints(endpoints: List<ArmeriaClientEndpoint>): List<ArmeriaClientEndpoint> {
        val query = searchField.text.trim().lowercase()
        if (query.isEmpty()) {
            return endpoints
        }
        return endpoints.filter { endpoint ->
            buildString {
                append(ArmeriaClientInvocationSupport.presentableLabel(endpoint))
                append(' ')
                append(endpoint.clientType)
                append(' ')
                append(endpoint.uri)
                append(' ')
                append(endpoint.target)
                append(' ')
                append(endpoint.moduleName)
                endpoint.endpointGroup?.let {
                    append(' ')
                    append(it)
                }
            }.lowercase().contains(query)
        }
    }

    private fun groupedItems(endpoints: List<ArmeriaClientEndpoint>): List<ClientListItem> {
        val sorted =
            endpoints.sortedWith(
                compareBy<ArmeriaClientEndpoint> { it.moduleName }
                    .thenBy { it.clientType }
                    .thenBy { ArmeriaClientInvocationSupport.presentableLabel(it) },
            )
        val items = mutableListOf<ClientListItem>()
        var currentGroup: String? = null
        for (endpoint in sorted) {
            val group = "${endpoint.moduleName} · ${endpoint.clientType}"
            if (group != currentGroup) {
                items += ClientListItem.Group(group)
                currentGroup = group
            }
            items += ClientListItem.Endpoint(endpoint)
        }
        return items
    }

    private fun refreshListPresentation() {
        val selected = selectedEndpoint()
        listModel.removeAllElements()
        groupedItems(filterEndpoints(collectedEndpoints)).forEach(listModel::addElement)
        if (selected != null) {
            selectEndpointNow(selected)
        } else {
            firstVisibleEndpoint()?.let(::selectEndpointNow)
        }
        clientDetailPanel.setEndpoint(selectedEndpoint())
    }

    private fun endpointIdentity(endpoint: ArmeriaClientEndpoint): Pair<String, Int>? {
        val fileUrl = endpoint.sourceFileUrl ?: return null
        val offset =
            endpoint.sourceOffset
                ?: ReadAction.computeBlocking<Int?, RuntimeException> { endpoint.pointer.range?.startOffset }
                ?: return null
        return fileUrl to offset
    }

    private fun updateStatusLabel(collectedEndpoints: List<ArmeriaClientEndpoint>) {
        statusLabel.text =
            if (collectedEndpoints.isEmpty()) {
                message("client.explorer.summary.empty")
            } else {
                buildString {
                    append(message("client.explorer.summary.endpoints", collectedEndpoints.size))
                    append(" · ")
                    append(
                        collectedEndpoints.groupBy { it.clientType }.entries.joinToString {
                            message("client.explorer.summary.typeBreakdown", it.key, it.value.size)
                        },
                    )
                }
            }
    }

    private fun navigateToSelection() {
        val endpoint = selectedEndpoint() ?: return
        ArmeriaRouteNavigation.navigateToPointer(
            project,
            endpoint.pointer,
            endpoint.sourceOffset,
            parentDisposable = this,
        )
    }

    private fun formatListLabel(endpoint: ArmeriaClientEndpoint): String {
        val suffix =
            buildString {
                if (!endpoint.transport.isNullOrEmpty()) {
                    append(" · ")
                    append(endpoint.transport)
                }
                if (endpoint.decorators.isNotEmpty()) {
                    append(" · ")
                    append(message("client.explorer.secondary.decorators", endpoint.decorators.joinToString()))
                }
            }
        return "${ArmeriaClientInvocationSupport.presentableLabel(endpoint)}$suffix"
    }

    private fun buildClientTooltip(endpoint: ArmeriaClientEndpoint): String =
        buildString {
            append(ArmeriaClientInvocationSupport.presentableLabel(endpoint))
            append(" → ")
            append(endpoint.target)
            if (!endpoint.transport.isNullOrEmpty()) {
                append(" · ")
                append(endpoint.transport)
            }
            if (!endpoint.endpointGroup.isNullOrEmpty()) {
                append(" · ")
                append(endpoint.endpointGroup)
            }
            if (endpoint.decorators.isNotEmpty()) {
                append(" · ")
                append(message("client.explorer.secondary.decorators", endpoint.decorators.joinToString()))
            }
            append(" (")
            append(endpoint.moduleName)
            append(')')
        }

    override fun dispose() = Unit
}

private sealed interface ClientListItem {
    data class Group(
        val label: String,
    ) : ClientListItem

    data class Endpoint(
        val endpoint: ArmeriaClientEndpoint,
    ) : ClientListItem
}
