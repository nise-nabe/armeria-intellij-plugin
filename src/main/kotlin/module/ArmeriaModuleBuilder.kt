@file:Suppress("UnstableApiUsage")

package com.linecorp.intellij.plugins.armeria.module

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.GeneratorTemplateFile
import com.intellij.ide.starters.local.Starter
import com.intellij.ide.starters.local.StarterModuleBuilder
import com.intellij.ide.starters.local.StarterPack
import com.intellij.ide.starters.shared.JAVA_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.MAVEN_PROJECT
import com.intellij.ide.starters.shared.StarterLanguage
import com.intellij.ide.starters.shared.StarterProjectType
import com.intellij.ide.starters.shared.StarterTestRunner
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.ProjectManager
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.starters.SCALA_STARTER_LANGUAGE
import javax.swing.Icon

class ArmeriaModuleBuilder: StarterModuleBuilder() {
    override fun getModuleType() = JavaModuleType()

    /**
     * setup module after 'finish' button pressed
     */
    override fun getAssets(starter: Starter): List<GeneratorAsset> {
        val ftManager = FileTemplateManager.getInstance(ProjectManager.getInstance().defaultProject)
        val assets = mutableListOf<GeneratorAsset>()
        when (starterContext.projectType) {
            GRADLE_KTS_PROJECT -> {
                assets.add(GeneratorTemplateFile(
                    "build.gradle.kts",
                    ftManager.getJ2eeTemplate("armeria-build.gradle.kts")
                ))
                assets.add(GeneratorTemplateFile(
                    "settings.gradle.kts",
                    ftManager.getJ2eeTemplate("armeria-settings.gradle.kts")
                ))
                assets.add(GeneratorTemplateFile(
                    "gradle.properties",
                    ftManager.getJ2eeTemplate("armeria-gradle.properties")
                ))
            }
            GRADLE_GROOVY_PROJECT -> {
                assets.add(GeneratorTemplateFile(
                    "build.gradle",
                    ftManager.getJ2eeTemplate("armeria-build.gradle")
                ))
                assets.add(GeneratorTemplateFile(
                    "settings.gradle",
                    ftManager.getJ2eeTemplate("armeria-settings.gradle")
                ))
                assets.add(GeneratorTemplateFile(
                    "gradle.properties",
                    ftManager.getJ2eeTemplate("armeria-gradle.properties")
                ))
            }
            MAVEN_PROJECT -> {
                assets.add(GeneratorTemplateFile(
                    "pom.xml",
                    ftManager.getJ2eeTemplate("armeria-pom.xml")
                ))
            }
        }


        val languageDir = when (starterContext.language) {
            KOTLIN_STARTER_LANGUAGE -> "kotlin"
            SCALA_STARTER_LANGUAGE -> "scala"
            else -> "java"
        }
        val packageDir = starterContext.group.split(".")
        assets.add(GeneratorEmptyDirectory((listOf("src", "main", languageDir) + packageDir).joinToString("/")))
        assets.add(GeneratorEmptyDirectory((listOf("src", "test", languageDir) + packageDir).joinToString("/")))

        return assets
    }

    override fun getBuilderId(): String = "armeria"

    override fun getDescription(): String = message("module.builder.armeria.description")

    override fun getLanguages(): List<StarterLanguage> {
        return listOf(KOTLIN_STARTER_LANGUAGE, JAVA_STARTER_LANGUAGE, SCALA_STARTER_LANGUAGE)
    }

    override fun getNodeIcon(): Icon = ArmeriaIcons.Armeria

    override fun getPresentableName(): String = message("module.type.armeria.name")

    override fun getProjectTypes(): List<StarterProjectType> {
        return listOf(GRADLE_KTS_PROJECT, GRADLE_GROOVY_PROJECT, MAVEN_PROJECT)
    }

    override fun getStarterPack(): StarterPack {
        return StarterPack("armeria", listOf(
            Starter("server", "Server", getDependencyConfig("/starters/armeria.pom"), listOf(
                ArmeriaBrave,
                ArmeriaDropwizard2,
                ArmeriaEureka,
                ArmeriaGrpc,
                ArmeriaJetty,
                ArmeriaKafka,
                ArmeriaKotlin,
                ArmeriaLogback,
                ArmeriaProtobuf,
                ArmeriaRetrofit,
                ArmeriaRxJava3,
                ArmeriaSaml,
                ArmeriaScala212,
                ArmeriaScala213,
                ArmeriaScala3,
                ArmeriaScalaPB2_12,
                ArmeriaScalaPB2_13,
                ArmeriaSpringBoot2,
                ArmeriaSpringBoot2WebFlux,
                ArmeriaSpringBoot3,
                ArmeriaSpringBoot3WebFlux,
                ArmeriaThrift0_13,
                ArmeriaTomcat8,
                ArmeriaTomcat,
                ArmeriaZookeeper,
            )),
            Starter("client", "Client", getDependencyConfig("/starters/armeria.pom"), listOf(
                ArmeriaGrpc,
                ArmeriaThrift0_13,
                ArmeriaRetrofit,
            ))
        ))
    }

    override fun getTestFrameworks(): List<StarterTestRunner> {
        return listOf(JUNIT5_TEST_RUNNER, JUNIT4_TEST_RUNNER)
    }
}
