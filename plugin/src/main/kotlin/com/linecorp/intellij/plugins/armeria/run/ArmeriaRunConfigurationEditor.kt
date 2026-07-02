package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.JComboBox
import javax.swing.JComponent

class ArmeriaRunConfigurationEditor(private val project: Project) : SettingsEditor<ArmeriaRunConfiguration>() {
    private val mainPanel = JBPanel<JBPanel<*>>()
    private val moduleComboBox = JComboBox<Module>()
    private val mainClassField = TextFieldWithBrowseButton()

    init {
        ModuleManager.getInstance(project).sortedModules.forEach(moduleComboBox::addItem)
        mainClassField.addActionListener {
            val chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
                message("armeria.run.configuration.main.class.chooser.title"),
                GlobalSearchScope.projectScope(project),
                { PsiMethodUtil.hasMainInClass(it) },
                null,
            )
            chooser.showDialog()
            val selectedClass = chooser.selected
            if (selectedClass != null) {
                mainClassField.text = selectedClass.qualifiedName.orEmpty()
            }
        }
        val formBuilder = FormBuilder.createFormBuilder()
        formBuilder.addLabeledComponent(message("armeria.run.configuration.module"), moduleComboBox)
        formBuilder.addLabeledComponent(message("armeria.run.configuration.main.class"), mainClassField)
        mainPanel.add(formBuilder.panel)
    }

    override fun resetEditorFrom(configuration: ArmeriaRunConfiguration) {
        moduleComboBox.selectedItem = configuration.getConfigurationModule().module
        mainClassField.text = configuration.getMainClass().orEmpty()
    }

    override fun applyEditorTo(configuration: ArmeriaRunConfiguration) {
        configuration.setModule(moduleComboBox.selectedItem as? Module)
        configuration.setMainClass(mainClassField.text.takeIf { it.isNotBlank() })
    }

    override fun createEditor(): JComponent = mainPanel
}
