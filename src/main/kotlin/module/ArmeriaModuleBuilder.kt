@file:Suppress("UnstableApiUsage")

package com.nisecoder.intellij.plugins.armeria.module

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.Starter
import com.intellij.ide.starters.local.StarterModuleBuilder
import com.intellij.ide.starters.local.StarterPack
import com.intellij.ide.starters.shared.GRADLE_PROJECT
import com.intellij.ide.starters.shared.JAVA_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.JUNIT_TEST_RUNNER
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.StarterLanguage
import com.intellij.ide.starters.shared.StarterProjectType
import com.intellij.ide.starters.shared.StarterTestRunner
import com.intellij.openapi.project.ProjectManager
import com.nisecoder.intellij.plugins.armeria.ArmeriaIcons
import com.nisecoder.intellij.plugins.armeria.message
import javax.swing.Icon

class ArmeriaModuleBuilder: StarterModuleBuilder() {
    override fun getModuleType() = ArmeriaModuleType()

    /**
     * setup module after 'finish' button pressed
     */
    override fun getAssets(starter: Starter): List<GeneratorAsset> {
        val ftManager = FileTemplateManager.getInstance(ProjectManager.getInstance().defaultProject)
        val assets = mutableListOf<GeneratorAsset>()
        when (starterContext.projectType) {
            GRADLE_PROJECT -> {
                assets.add(GeneratorTemplateFile(
                    "build.gradle.kts",
                    ftManager.getJ2eeTemplate("armeria-build.gradle.kts")
                ))
                assets.add(GeneratorTemplateFile(
                    "settings.gradle.kts",
                    ftManager.getJ2eeTemplate("armeria-settings.gradle.kts")
                ))
            }
        }
        return assets
    }

    override fun getBuilderId(): String = "armeria"

    override fun getDescription(): String = message("module.builder.armeria.description")

    override fun getLanguages(): List<StarterLanguage> {
        return listOf(JAVA_STARTER_LANGUAGE, KOTLIN_STARTER_LANGUAGE)
    }

    override fun getNodeIcon(): Icon = ArmeriaIcons.Armeria

    override fun getPresentableName(): String = message("module.type.armeria.name")

    override fun getProjectTypes(): List<StarterProjectType> {
        return listOf(GRADLE_PROJECT)
    }

    override fun getStarterPack(): StarterPack {
        return StarterPack("armeria", listOf(
            Starter("server", "Server", getDependencyConfig("/starters/armeria.pom"), listOf()),
            Starter("client", "Client", getDependencyConfig("/starters/armeria.pom"), listOf())
        ))
    }

    override fun getTestFrameworks(): List<StarterTestRunner> {
        return listOf(JUNIT_TEST_RUNNER)
    }
}
