package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteDetailFormatter
import com.linecorp.intellij.plugins.armeria.message
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Structured detail view for a single selected [ArmeriaRoute].
 */
class ArmeriaRouteDetailPanel : JPanel(BorderLayout()) {
    private val detailStatus = wrappingValueLabel()
    private val detailMethod = JBLabel()
    private val detailProtocol = JBLabel()
    private val detailPath = JBLabel()
    private val detailModule = JBLabel()
    private val detailRegistration = wrappingValueLabel()
    private val detailRegisteredIn = wrappingValueLabel()
    private val detailHandler = wrappingValueLabel()
    private val detailDefinition = wrappingValueLabel()
    private val detailAttachments = wrappingValueLabel()
    private val handlerFieldLabel = JBLabel(message("route.explorer.detail.handler"))

    private val registeredInRow: JPanel
    private val definitionRow: JPanel
    private val attachmentsBlock: JPanel

    init {
        val overviewBody =
            formBody(
                labeledField(message("route.explorer.detail.status"), detailStatus),
                labeledField(message("route.explorer.detail.method"), detailMethod),
                labeledField(message("route.explorer.detail.protocol"), detailProtocol),
                labeledField(message("route.explorer.detail.path"), detailPath),
                labeledField(message("route.explorer.detail.module"), detailModule),
            )
        registeredInRow = labeledField(message("route.explorer.detail.registeredIn"), detailRegisteredIn)
        definitionRow = labeledField(message("route.explorer.detail.definition"), detailDefinition)
        val registrationBody =
            formBody(
                labeledField(message("route.explorer.detail.registration"), detailRegistration),
                registeredInRow,
            )
        val implementationBody =
            formBody(
                labeledField(handlerFieldLabel, detailHandler),
                definitionRow,
            )
        attachmentsBlock =
            detailSection(
                message("route.explorer.detail.block.attachments"),
                JPanel(BorderLayout()).apply {
                    add(detailAttachments, BorderLayout.CENTER)
                },
            )

        add(
            JPanel(
                com.intellij.ui.components.panels
                    .VerticalLayout(JBUI.scale(10)),
            ).apply {
                add(detailSection(message("route.explorer.detail.block.overview"), overviewBody))
                add(detailSection(message("route.explorer.detail.block.registration"), registrationBody))
                add(detailSection(message("route.explorer.detail.block.implementation"), implementationBody))
                add(attachmentsBlock)
            },
            BorderLayout.CENTER,
        )
        clear()
    }

    fun setRoute(route: ArmeriaRoute?) {
        if (route == null) {
            clear()
            return
        }
        handlerFieldLabel.text = route.detailHandlerLabel
        detailMethod.text = route.methodLabel
        detailProtocol.text = route.protocol
        detailPath.text = route.path
        detailModule.text = route.moduleName
        setWrappingText(detailHandler, route.target, route.target)
        setWrappingText(detailStatus, ArmeriaRouteDetailFormatter.statusLine(route))
        WriteIntentReadAction.run {
            setWrappingText(detailRegistration, route.resolveRegistrationSummary())
            val registeredInHint = route.resolveRegisteredInHint()
            setWrappingText(detailRegisteredIn, registeredInHint)
            registeredInRow.isVisible = registeredInHint.isNotEmpty()

            val definitionHint = route.resolveSourceHint()
            setWrappingText(detailDefinition, definitionHint)
            definitionRow.isVisible = definitionHint.isNotEmpty()

            val attachmentsText = ArmeriaRouteDetailFormatter.attachmentsLine(route)
            setWrappingText(detailAttachments, attachmentsText)
            attachmentsBlock.isVisible = attachmentsText.isNotEmpty()
        }
    }

    fun clear() {
        handlerFieldLabel.text = message("route.explorer.detail.handler")
        setWrappingText(detailStatus, "")
        setWrappingText(detailRegistration, "")
        setWrappingText(detailRegisteredIn, "")
        detailMethod.text = ""
        detailProtocol.text = ""
        detailPath.text = ""
        detailModule.text = ""
        setWrappingText(detailHandler, "")
        setWrappingText(detailDefinition, "")
        setWrappingText(detailAttachments, "")
        registeredInRow.isVisible = false
        definitionRow.isVisible = false
        attachmentsBlock.isVisible = false
    }

    private fun detailSection(
        title: String,
        body: JComponent,
    ): JPanel =
        JPanel(BorderLayout()).apply {
            add(TitledSeparator(title), BorderLayout.NORTH)
            add(
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.emptyLeft(12)
                    add(body, BorderLayout.CENTER)
                },
                BorderLayout.CENTER,
            )
        }

    private fun formBody(vararg rows: JComponent): JPanel {
        val builder = FormBuilder.createFormBuilder()
        rows.forEach { builder.addComponent(it) }
        return builder.panel
    }

    private fun labeledField(
        label: String,
        value: JComponent,
    ): JPanel = labeledField(JBLabel(label), value)

    private fun labeledField(
        label: JBLabel,
        value: JComponent,
    ): JPanel =
        FormBuilder
            .createFormBuilder()
            .addLabeledComponent(label, value, 1, false)
            .panel

    private fun wrappingValueLabel(): JBLabel =
        JBLabel().apply {
            putClientProperty("html.disable", false)
        }

    private fun detailWrapWidth(): Int = JBUI.scale(280)

    private fun setWrappingText(
        label: JBLabel,
        text: String,
        toolTip: String? = null,
    ) {
        if (text.isEmpty()) {
            label.text = ""
            label.toolTipText = null
            return
        }
        label.text = "<html><body width='${detailWrapWidth()}'>${escapeHtml(text)}</body></html>"
        label.toolTipText = toolTip?.takeIf { it.length > 80 } ?: text.takeIf { it.length > 80 }
    }

    private fun escapeHtml(text: String): String = StringUtil.escapeXmlEntities(text).replace("\n", "<br/>")
}
