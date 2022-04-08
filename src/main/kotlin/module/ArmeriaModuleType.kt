package com.linecorp.intellij.plugins.armeria.module

import com.intellij.openapi.module.ModuleType
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.Icon

class ArmeriaModuleType: ModuleType<ArmeriaModuleBuilder>(ARMERIA_MODULE) {
    override fun createModuleBuilder(): ArmeriaModuleBuilder = ArmeriaModuleBuilder()

    override fun getName() = message("module.type.armeria.name")

    override fun getDescription() = message("module.type.armeria.description")

    override fun getNodeIcon(isOpened: Boolean): Icon = ArmeriaIcons.Armeria
}
