import com.nisecoder.gradle.plugin.idea.ext.packagePrefix
import com.nisecoder.gradle.plugin.idea.ext.settings
import org.jetbrains.changelog.ChangelogPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.changelog")
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
    plugins.set(listOf("org.jetbrains.plugins.gradle"))
}

tasks {
    runIde {
        autoReloadPlugins.set(true)
    }
    val changelog: ChangelogPluginExtension = extensions.getByType()
    patchPluginXml {
        changeNotes.set(provider { changelog.getLatest().toHTML()} )
    }

    asciidoctor {
        // collect into root buildDirectory for publishing to GitHub Pages
        val outputDir = if (project == rootProject) {
            rootProject.buildDir.resolve("docs/asciidoc")
        } else {
            rootProject.buildDir.resolve("docs/asciidoc/${project.name}")
        }
        setOutputDir(outputDir)
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            targetCompatibility = JavaVersion.VERSION_11.toString()
            apiVersion = "1.5"
            languageVersion = "1.5"
            javaParameters = true
        }
    }
}
