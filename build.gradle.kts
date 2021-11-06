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
    id("com.nisecoder.github-pages.asciidoctor")
    id("org.asciidoctor.jvm.convert")
    `maven-publish`
}

group = "com.nisecoder.intellij"
// inject in GitHub Action Publish Workflow
val publishVersion: String? by project
version = if (publishVersion?.isNotEmpty() == true) {
    publishVersion!!.replaceFirst("refs/tags/v", "")
} else {
    "1.0-SNAPSHOT"
}

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
    version.set("2021.2.3")
    type.set("IU")
    downloadSources.set(true)
    plugins.set(listOf(
        "org.jetbrains.plugins.gradle",
        "grpc"
    ))
}

tasks {
    runIde {
        autoReloadPlugins.set(true)
    }
    val changelog: ChangelogPluginExtension = extensions.getByType()
    patchPluginXml {
        changeNotes.set(provider { changelog.getLatest().toHTML()} )
    }

    withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_11.toString()
        targetCompatibility = JavaVersion.VERSION_11.toString()
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

publishing {
    repositories {
        maven {
            name = "gitHubPackages"
            url = uri("https://maven.pkg.github.com/nise-nabe/armeria-intellij-plugin")
            // set ~/.gradle/gradle.properties
            // gitHubPackagesUsername
            // gitHubPackagesPassword
            credentials(PasswordCredentials::class)
        }
    }
    publications {
        create<MavenPublication>("plugin") {
            artifact(tasks.getByName("buildPlugin"))
        }
    }
}
