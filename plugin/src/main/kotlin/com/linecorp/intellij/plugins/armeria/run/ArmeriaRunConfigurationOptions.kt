package com.linecorp.intellij.plugins.armeria.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class ArmeriaRunConfigurationOptions : LocatableRunConfigurationOptions() {
    private val mainClass: StoredProperty<String?> = string("").provideDelegate(this, "mainClass")
    val moduleName: StoredProperty<String?> = string("").provideDelegate(this, "moduleName")
    private val verboseResponses: StoredProperty<Boolean> = property(false).provideDelegate(this, "verboseResponses")
    private val reportBlockedEventLoop: StoredProperty<Boolean> =
        property(false).provideDelegate(this, "reportBlockedEventLoop")
    private val openDocServiceAfterLaunch: StoredProperty<Boolean> =
        property(false).provideDelegate(this, "openDocServiceAfterLaunch")

    fun getMainClass(): String? = mainClass.getValue(this)

    fun setMainClass(value: String?) {
        mainClass.setValue(this, value)
    }

    fun isVerboseResponses(): Boolean = verboseResponses.getValue(this)

    fun setVerboseResponses(value: Boolean) {
        verboseResponses.setValue(this, value)
    }

    fun isReportBlockedEventLoop(): Boolean = reportBlockedEventLoop.getValue(this)

    fun setReportBlockedEventLoop(value: Boolean) {
        reportBlockedEventLoop.setValue(this, value)
    }

    fun isOpenDocServiceAfterLaunch(): Boolean = openDocServiceAfterLaunch.getValue(this)

    fun setOpenDocServiceAfterLaunch(value: Boolean) {
        openDocServiceAfterLaunch.setValue(this, value)
    }
}
