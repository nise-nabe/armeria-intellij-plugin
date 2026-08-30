package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.JComboBox
import javax.swing.JComponent

class ArmeriaRunConfigurationEditor(
    private val project: Project,
) : SettingsEditor<ArmeriaRunConfiguration>() {
    private val mainPanel = JBPanel<JBPanel<*>>()
    private val moduleComboBox = JComboBox<Module>()
    private val mainClassField = TextFieldWithBrowseButton()
    private val verboseResponsesCheckBox = JBCheckBox(message("armeria.run.configuration.verboseResponses"))
    private val reportBlockedEventLoopCheckBox =
        JBCheckBox(message("armeria.run.configuration.reportBlockedEventLoop"))
    private val openDocServiceCheckBox = JBCheckBox(message("armeria.run.configuration.openDocService"))

    init {
        ModuleManager.getInstance(project).sortedModules.forEach(moduleComboBox::addItem)
        mainClassField.addActionListener {
            val selectedModule = moduleComboBox.selectedItem as? Module
            val searchScope =
                selectedModule?.let { GlobalSearchScope.moduleScope(it) }
                    ?: GlobalSearchScope.projectScope(project)
            val chooser =
                TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
                    message("armeria.run.configuration.main.class.chooser.title"),
                    searchScope,
                    { psiClass ->
                        PsiMethodUtil.hasMainInClass(psiClass) || PsiMethodUtil.hasMainMethod(psiClass)
                    },
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
        formBuilder.addComponent(verboseResponsesCheckBox)
        formBuilder.addComponent(reportBlockedEventLoopCheckBox)
        formBuilder.addComponent(openDocServiceCheckBox)
        mainPanel.add(formBuilder.panel)
    }

    override fun resetEditorFrom(configuration: ArmeriaRunConfiguration) {
        val configuredModule =
            configuration.getConfigurationModule().module
                ?: ModuleManager.getInstance(project).sortedModules.firstOrNull()
        moduleComboBox.selectedItem = configuredModule
        mainClassField.text = configuration.getMainClass().orEmpty()
        verboseResponsesCheckBox.isSelected = configuration.isVerboseResponses()
        reportBlockedEventLoopCheckBox.isSelected = configuration.isReportBlockedEventLoop()
        openDocServiceCheckBox.isSelected = configuration.isOpenDocServiceAfterLaunch()
    }

    override fun applyEditorTo(configuration: ArmeriaRunConfiguration) {
        configuration.setModule(moduleComboBox.selectedItem as? Module)
        configuration.setMainClass(mainClassField.text.takeIf { it.isNotBlank() })
        configuration.setVerboseResponses(verboseResponsesCheckBox.isSelected)
        configuration.setReportBlockedEventLoop(reportBlockedEventLoopCheckBox.isSelected)
        configuration.setOpenDocServiceAfterLaunch(openDocServiceCheckBox.isSelected)
    }

    override fun createEditor(): JComponent = mainPanel
}
