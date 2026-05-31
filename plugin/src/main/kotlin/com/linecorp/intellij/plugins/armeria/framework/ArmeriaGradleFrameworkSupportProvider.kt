package com.linecorp.intellij.plugins.armeria.framework

import com.intellij.framework.FrameworkTypeEx
import org.jetbrains.plugins.gradle.frameworkSupport.KotlinDslGradleFrameworkSupportProvider

class ArmeriaGradleFrameworkSupportProvider: KotlinDslGradleFrameworkSupportProvider() {
    override fun getFrameworkType(): FrameworkTypeEx = ArmeriaFrameworkEx()
}
