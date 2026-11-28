package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceEndpointValidator
import com.linecorp.intellij.plugins.armeria.message
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JComponent

class ArmeriaDocServiceSyncDialog(
    project: Project,
    defaultHost: String,
    defaultPort: String,
    defaultMountPath: String,
    defaultUseHttps: Boolean,
    listenPortBindings: List<Pair<Int, Boolean>> = emptyList(),
) : DialogWrapper(project) {
    private val hostField = JBTextField(defaultHost, 0)
    private val portField = JBTextField(defaultPort, 0)
    private val mountPathField = JBTextField(defaultMountPath, 0)
    private val useHttpsCheckBox = JBCheckBox(message("route.explorer.sync.useHttps"), defaultUseHttps)
    private val listenPortChoices = listenPortBindings.map { ListenPortChoice(it.first, it.second) }
    private val listenPortCombo =
        JComboBox(listenPortChoices.toTypedArray()).apply {
            val defaultPortNumber = defaultPort.toIntOrNull()
            val defaultIndex = listenPortChoices.indexOfFirst { it.port == defaultPortNumber }
            if (listenPortChoices.isNotEmpty()) {
                selectedIndex = if (defaultIndex >= 0) defaultIndex else 0
            }
            addActionListener {
                (selectedItem as? ListenPortChoice)?.let { choice ->
                    portField.text = choice.port.toString()
                    useHttpsCheckBox.isSelected = choice.https
                }
            }
        }

    init {
        title = message("route.explorer.action.syncRuntime")
        init()
    }

    val host: String
        get() = hostField.text.trim()

    val portText: String
        get() = portField.text.trim()

    val mountPath: String
        get() = mountPathField.text.trim()

    val useHttps: Boolean
        get() = useHttpsCheckBox.isSelected

    override fun createCenterPanel(): JComponent =
        FormBuilder
            .createFormBuilder()
            .apply {
                if (listenPortChoices.size > 1) {
                    addLabeledComponent(message("route.explorer.sync.detectedPort.prompt"), listenPortCombo)
                }
            }.addLabeledComponent(message("route.explorer.sync.host.prompt"), hostField)
            .addLabeledComponent(message("route.explorer.sync.port.prompt"), portField)
            .addLabeledComponent(message("route.explorer.sync.mountPath.prompt"), mountPathField)
            .addComponent(useHttpsCheckBox)
            .panel
            .apply {
                preferredSize = Dimension(JBUI.scale(420), preferredSize.height)
            }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        when (ArmeriaDocServiceEndpointValidator.validateHost(host)) {
            "empty" -> return com.intellij.openapi.ui.ValidationInfo(
                message("route.explorer.sync.error.hostEmpty"),
                hostField,
            )

            "invalid" -> return com.intellij.openapi.ui.ValidationInfo(
                message("route.explorer.sync.error.hostInvalid"),
                hostField,
            )

            null -> Unit
        }
        if (ArmeriaDocServiceEndpointValidator.validatePort(portText) == null) {
            return com.intellij.openapi.ui.ValidationInfo(
                message("route.explorer.sync.error.portInvalid"),
                portField,
            )
        }
        return null
    }

    private data class ListenPortChoice(
        val port: Int,
        val https: Boolean,
    ) {
        override fun toString(): String = "${if (https) "https" else "http"}://localhost:$port"
    }
}
