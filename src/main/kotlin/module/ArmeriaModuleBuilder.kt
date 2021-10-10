package com.nisecoder.intellij.plugins.armeria.module

import com.intellij.ide.util.projectWizard.ModuleBuilder

class ArmeriaModuleBuilder: ModuleBuilder() {
    override fun getModuleType() = ArmeriaModuleType()
}
