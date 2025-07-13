package com.linecorp.intellij.plugins.armeria.run

import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.FormBuilder
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.JComponent

class ArmeriaRunConfigurationEditor(private val project: Project) : SettingsEditor<ArmeriaRunConfiguration>() {
    private val mainPanel: JBPanel<JBPanel<*>> = JBPanel()
    private val mainClassField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()

    init {
        setupMainClassField()

        val formBuilder = FormBuilder.createFormBuilder()
        formBuilder.addLabeledComponent(message("armeria.run.configuration.main.class"), mainClassField)
        mainPanel.add(formBuilder.panel)
    }

    private fun setupMainClassField() {
        mainClassField.addActionListener { 
            val chooser = TreeClassChooserFactory.getInstance(project).createWithInnerClassesScopeChooser(
                "Choose Main Class",
                GlobalSearchScope.projectScope(project),
                ClassFilter { psiClass ->
                    // Filter for classes with main method
                    psiClass.findMethodsByName("main", false).any { method ->
                        // Check if method is public static void main(String[] args)
                        val modifierList = method.modifierList
                        val isPublic = modifierList?.hasModifierProperty("public") == true
                        val isStatic = modifierList?.hasModifierProperty("static") == true
                        val isVoid = method.returnType?.canonicalText == "void"
                        val hasCorrectParameters = method.parameterList.parametersCount == 1 &&
                            method.parameterList.parameters[0].type.canonicalText.let { paramType ->
                                paramType == "java.lang.String[]" || paramType == "String[]"
                            }

                        isPublic && isStatic && isVoid && hasCorrectParameters
                    }
                },
                null
            )

            chooser.showDialog()
            val selectedClass = chooser.selected
            if (selectedClass != null) {
                mainClassField.text = selectedClass.qualifiedName ?: ""
            }
        }
    }

    override fun resetEditorFrom(configuration: ArmeriaRunConfiguration) {
        mainClassField.text = configuration.getMainClass() ?: ""
    }

    override fun applyEditorTo(configuration: ArmeriaRunConfiguration) {
        configuration.setMainClass(mainClassField.text.takeIf { it.isNotBlank() })
    }

    override fun createEditor(): JComponent = mainPanel
}
