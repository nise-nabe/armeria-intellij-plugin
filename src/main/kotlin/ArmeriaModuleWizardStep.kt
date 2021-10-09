package com.nisecoder.intellij.plugins.armeria

import com.intellij.ide.util.projectWizard.ModuleBuilder

class ArmeriaModuleWizardStep: ModuleBuilder() {
    override fun getModuleType() = ArmeriaModuleType()
}
