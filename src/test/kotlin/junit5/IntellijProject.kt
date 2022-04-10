package com.linecorp.intellij.plugins.armeria.junit5

import com.intellij.ide.fileTemplates.FileTemplateManager
import java.io.File


data class IntellijProject(
    val testProjectDir: File,
    var ftManager: FileTemplateManager,
) {
    val settingsFile: File get() = testProjectDir.resolve("settings.gradle.kts")
    val buildFile: File get() = testProjectDir.resolve("build.gradle.kts")
}
