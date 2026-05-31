package com.linecorp.intellij.plugins.armeria.framework

import com.intellij.framework.FrameworkTypeEx
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaFrameworkEx: FrameworkTypeEx(ARMERIA_FRAMEWORK) {
    override fun getPresentableName() = message("framework.type.name")

    override fun getIcon() = ArmeriaIcons.Armeria

    override fun createProvider() = ArmeriaGradleFrameworkSupportProvider()
}
