import com.nisecoder.gradle.plugin.idea.ext.packagePrefix
import com.nisecoder.gradle.plugin.idea.ext.settings

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("com.nisecoder.idea-ext-ext")
    id("org.asciidoctor.jvm.convert")
}

group = "com.nisecoder.intellij"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

idea {
    module {
        settings {
            packagePrefix["src/main/kotlin"] = "com.nisecoder.intellij.plugins.armeria"
        }
    }
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("2021.2.2")
    type.set("IU")
    downloadSources.set(true)
    plugins.set(listOf())
}

tasks {
    runIde {
        autoReloadPlugins.set(true)
    }
    patchPluginXml {
        changeNotes.set("""
            Add change notes here.<br>
            <em>most HTML tags may be used</em>        """.trimIndent())
    }

    asciidoctor {
        // collect into root buildDirectory for publishing to Github Pages
        val outputDir = if (project == rootProject) {
            rootProject.buildDir.resolve("docs/asciidoc")
        } else {
            rootProject.buildDir.resolve("docs/asciidoc/${project.name}")
        }
        setOutputDir(outputDir)
    }
}
