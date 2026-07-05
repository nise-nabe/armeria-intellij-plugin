package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ArmeriaClientDetailPanel : JPanel(BorderLayout()) {
    private val detailClientType = JBLabel()
    private val detailUri = wrappingValueLabel()
    private val detailTarget = wrappingValueLabel()
    private val detailModule = JBLabel()
    private val detailTransport = JBLabel()
    private val detailEndpointGroup = wrappingValueLabel()
    private val detailDecorators = wrappingValueLabel()

    private val transportRow: JPanel
    private val endpointGroupRow: JPanel
    private val decoratorsBlock: JPanel

    init {
        val overviewBody = formBody(
            labeledField(message("client.explorer.detail.clientType"), detailClientType),
            labeledField(message("client.explorer.detail.uri"), detailUri),
            labeledField(message("client.explorer.detail.target"), detailTarget),
            labeledField(message("client.explorer.detail.module"), detailModule),
        )
        transportRow = labeledField(message("client.explorer.detail.transport"), detailTransport)
        endpointGroupRow = labeledField(message("client.explorer.detail.endpointGroup"), detailEndpointGroup)
        decoratorsBlock = detailSection(
            message("client.explorer.detail.block.decorators"),
            JPanel(BorderLayout()).apply {
                add(detailDecorators, BorderLayout.CENTER)
            },
        )

        add(
            JPanel(com.intellij.ui.components.panels.VerticalLayout(JBUI.scale(10))).apply {
                add(detailSection(message("client.explorer.detail.block.overview"), overviewBody))
                add(
                    detailSection(
                        message("client.explorer.detail.block.metadata"),
                        formBody(transportRow, endpointGroupRow),
                    ),
                )
                add(decoratorsBlock)
            },
            BorderLayout.CENTER,
        )
        clear()
    }

    fun setEndpoint(endpoint: ArmeriaClientEndpoint?) {
        if (endpoint == null) {
            clear()
            return
        }
        detailClientType.text = endpoint.clientType
        setWrappingText(detailUri, endpoint.uri)
        setWrappingText(detailTarget, endpoint.target)
        detailModule.text = endpoint.moduleName

        val transport = endpoint.transport
        detailTransport.text = transport.orEmpty()
        transportRow.isVisible = !transport.isNullOrEmpty()

        val endpointGroup = endpoint.endpointGroup
        setWrappingText(detailEndpointGroup, endpointGroup.orEmpty())
        endpointGroupRow.isVisible = !endpointGroup.isNullOrEmpty()

        val decoratorsText = endpoint.decorators.joinToString()
        setWrappingText(detailDecorators, decoratorsText)
        decoratorsBlock.isVisible = endpoint.decorators.isNotEmpty()
    }

    fun clear() {
        detailClientType.text = ""
        setWrappingText(detailUri, "")
        setWrappingText(detailTarget, "")
        detailModule.text = ""
        detailTransport.text = ""
        setWrappingText(detailEndpointGroup, "")
        setWrappingText(detailDecorators, "")
        transportRow.isVisible = false
        endpointGroupRow.isVisible = false
        decoratorsBlock.isVisible = false
    }

    private fun detailSection(title: String, body: JComponent): JPanel {
        return JPanel(BorderLayout()).apply {
            add(TitledSeparator(title), BorderLayout.NORTH)
            add(
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.emptyLeft(12)
                    add(body, BorderLayout.CENTER)
                },
                BorderLayout.CENTER,
            )
        }
    }

    private fun formBody(vararg rows: JComponent): JPanel {
        val builder = FormBuilder.createFormBuilder()
        rows.forEach { builder.addComponent(it) }
        return builder.panel
    }

    private fun labeledField(label: String, value: JComponent): JPanel =
        FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(label), value, 1, false)
            .panel

    private fun wrappingValueLabel(): JBLabel {
        return JBLabel().apply {
            putClientProperty("html.disable", false)
        }
    }

    private fun detailWrapWidth(): Int = JBUI.scale(280)

    private fun setWrappingText(label: JBLabel, text: String) {
        if (text.isEmpty()) {
            label.text = ""
            label.toolTipText = null
            return
        }
        label.text = "<html><body width='${detailWrapWidth()}'>${escapeHtml(text)}</body></html>"
        label.toolTipText = text.takeIf { it.length > 80 }
    }

    private fun escapeHtml(text: String): String =
        StringUtil.escapeXmlEntities(text).replace("\n", "<br/>")
}
