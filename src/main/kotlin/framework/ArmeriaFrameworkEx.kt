package com.nisecoder.intellij.plugins.armeria.framework

import com.intellij.framework.FrameworkTypeEx
import com.nisecoder.intellij.plugins.armeria.ArmeriaIcons
import com.nisecoder.intellij.plugins.armeria.message

class ArmeriaFrameworkEx: FrameworkTypeEx(ARMERIA_FRAMEWORK) {
    override fun getPresentableName() = message("framework.type.name")

    override fun getIcon() = ArmeriaIcons.Armeria

    override fun createProvider() = ArmeriaGradleFrameworkSupportProvider()
}
