package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
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
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.explorer.navigation.ArmeriaRouteNavigation
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class ArmeriaClientExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true),
    Disposable {
    private var initialRefreshScheduled = false

    private val listModel = DefaultListModel<ArmeriaClientEndpoint>()
    private val endpointList = JBList(listModel)
    private val statusLabel = JBLabel()
    private val clientDetailPanel = ArmeriaClientDetailPanel()

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
            }
        toolbar =
            ActionManager
                .getInstance()
                .createActionToolbar("ArmeriaClientExplorer", actionGroup, true)
                .also {
                    it.targetComponent = this
                }.component

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
                    val label = if (value is ArmeriaClientEndpoint) formatListLabel(value) else value?.toString()
                    val component = super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
                    toolTipText = null
                    if (value is ArmeriaClientEndpoint) {
                        toolTipText = buildClientTooltip(value)
                    }
                    return component
                }
            }
        endpointList.addListSelectionListener(
            ListSelectionListener { _: ListSelectionEvent ->
                clientDetailPanel.setEndpoint(endpointList.selectedValue)
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
            OnePixelSplitter(false, 0.65f).apply {
                firstComponent = listScrollPane
                secondComponent = detailPanel
                setHonorComponentsMinimumSize(false)
            }

        val contentPanel =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 8, 0, 8)
                add(statusLabel, BorderLayout.NORTH)
                add(splitter, BorderLayout.CENTER)
            }
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
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { collectedEndpoints ->
                listModel.removeAllElements()
                collectedEndpoints.forEach(listModel::addElement)
                updateStatusLabel(collectedEndpoints)
                clientDetailPanel.setEndpoint(endpointList.selectedValue)
            }.submit(AppExecutorUtil.getAppExecutorService())
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
        val endpoint = endpointList.selectedValue ?: return
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
        return "${endpoint.clientType} ${endpoint.uri}$suffix"
    }

    private fun buildClientTooltip(endpoint: ArmeriaClientEndpoint): String =
        buildString {
            append(endpoint.clientType)
            append(' ')
            append(endpoint.uri)
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
