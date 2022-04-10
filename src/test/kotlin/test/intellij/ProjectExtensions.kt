package com.linecorp.intellij.plugins.armeria.test.intellij

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.Project
import java.io.File


val Project.ftManager: FileTemplateManager get() = FileTemplateManager.getInstance(this)
val Project.settingsFile: File get() = File(basePath).resolve("settings.gradle.kts")
val Project.buildFile: File get() = File(basePath).resolve("build.gradle.kts")
