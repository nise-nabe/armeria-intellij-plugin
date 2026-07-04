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
import com.intellij.pom.Navigatable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.KeyStroke

class ArmeriaClientExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true), Disposable {
    private var initialRefreshScheduled = false

    private val listModel = DefaultListModel<ArmeriaClientEndpoint>()
    private val endpointList = JBList(listModel)
    private val statusLabel = JBLabel()

    init {
        val actionGroup = DefaultActionGroup().apply {
            add(object : DumbAwareAction(message("client.explorer.action.refresh")) {
                override fun actionPerformed(e: AnActionEvent) {
                    refresh()
                }
            })
        }
        toolbar = ActionManager.getInstance().createActionToolbar("ArmeriaClientExplorer", actionGroup, true).also {
            it.targetComponent = this
        }.component

        endpointList.emptyText.text = message("client.explorer.empty")
        endpointList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val label = if (value is ArmeriaClientEndpoint) "${value.clientType} ${value.uri}" else value?.toString()
                val component = super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
                if (value is ArmeriaClientEndpoint) {
                    toolTipText = buildString {
                        append(value.clientType)
                        append(' ')
                        append(value.uri)
                        append(" → ")
                        append(value.target)
                        append(" (")
                        append(value.moduleName)
                        append(')')
                    }
                }
                return component
            }
        }
        endpointList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    navigateToSelection()
                }
            }
        })
        endpointList.registerKeyboardAction(
            { navigateToSelection() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JBList.WHEN_FOCUSED,
        )

        val contentPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 0, 8)
            add(statusLabel, BorderLayout.NORTH)
            add(JBScrollPane(endpointList), BorderLayout.CENTER)
        }
        setContent(contentPanel)

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
        ReadAction.nonBlocking<List<ArmeriaClientEndpoint>> {
            ArmeriaClientCollector.collect(project)
        }
            .inSmartMode(project)
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { collectedEndpoints ->
                listModel.removeAllElements()
                collectedEndpoints.forEach(listModel::addElement)
                updateStatusLabel(collectedEndpoints)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun updateStatusLabel(collectedEndpoints: List<ArmeriaClientEndpoint>) {
        statusLabel.text = if (collectedEndpoints.isEmpty()) {
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
        ReadAction.nonBlocking<Navigatable?> {
            val element = endpoint.pointer.element as? Navigatable
            element?.takeIf { it.canNavigate() }
        }
            .inSmartMode(project)
            .expireWith(this)
            .finishOnUiThread(ModalityState.any()) { navigatable ->
                navigatable?.navigate(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun dispose() = Unit
}
