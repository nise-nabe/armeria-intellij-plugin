package com.nisecoder.intellij.plugins.armeria.module

import com.intellij.openapi.module.ModuleType
import com.nisecoder.intellij.plugins.armeria.ArmeriaIcons
import com.nisecoder.intellij.plugins.armeria.message
import javax.swing.Icon

class ArmeriaModuleType: ModuleType<ArmeriaModuleWizardStep>(ARMERIA_MODULE) {
    override fun createModuleBuilder(): ArmeriaModuleWizardStep = ArmeriaModuleWizardStep()

    override fun getName() = message("module.type.armeria.name")

    override fun getDescription() = message("module.type.armeria.description")

    override fun getNodeIcon(isOpened: Boolean): Icon = ArmeriaIcons.Armeria
}
