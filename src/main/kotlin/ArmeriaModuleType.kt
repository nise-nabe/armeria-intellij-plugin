package com.nisecoder.intellij.plugins.armeria

import com.intellij.openapi.module.ModuleType
import com.nisecoder.intellij.plugins.armeria.module.ARMERIA_MODULE
import javax.swing.Icon

class ArmeriaModuleType: ModuleType<ArmeriaModuleWizardStep>(ARMERIA_MODULE) {
    override fun createModuleBuilder(): ArmeriaModuleWizardStep = ArmeriaModuleWizardStep()

    override fun getName() = message("module.type.armeria.name")

    override fun getDescription() = message("module.type.armeria.description")

    override fun getNodeIcon(isOpened: Boolean): Icon = ArmeriaIcons.Armeria
}
