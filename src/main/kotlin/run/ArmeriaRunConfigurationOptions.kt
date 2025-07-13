package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class ArmeriaRunConfigurationOptions : LocatableRunConfigurationOptions() {
    private val mainClass: StoredProperty<String?> = string("").provideDelegate(this, "mainClass")

    fun getMainClass(): String? = mainClass.getValue(this)

    fun setMainClass(value: String?) {
        mainClass.setValue(this, value)
    }
}
