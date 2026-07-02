package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.linecorp.intellij.plugins.armeria.message
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel

class ArmeriaClientExplorerPanel(
    private val project: Project,
) : SimpleToolWindowPanel(true, true) {
    private val listModel = DefaultListModel<ArmeriaClientEndpoint>()
    private val endpointList = JBList(listModel)

    init {
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
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus)
            }
        }
        endpointList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    val endpoint = endpointList.selectedValue ?: return
                    (endpoint.pointer.element as? Navigatable)?.navigate(true)
                }
            }
        })
        setContent(JBScrollPane(endpointList))
    }

    fun refresh() {
        listModel.removeAllElements()
        ArmeriaClientCollector.collect(project).forEach(listModel::addElement)
    }
}
