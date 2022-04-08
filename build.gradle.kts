import org.jetbrains.changelog.ChangelogPluginExtension
import org.jetbrains.gradle.ext.packagePrefix
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij")
    id("org.jetbrains.changelog")
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("com.nisecoder.github-pages.asciidoctor")
    id("org.asciidoctor.jvm.convert")
    id("com.nisecoder.github-release-upload")
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
    version.set("2021.3")
    type.set("IU")
    downloadSources.set(true)
    plugins.set(listOf(
        "org.jetbrains.plugins.gradle",
        "grpc"
    ))
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks {
    runIde {
        autoReloadPlugins.set(true)
    }
    val changelog: ChangelogPluginExtension = extensions.getByType()
    patchPluginXml {
        changeNotes.set(provider { changelog.getLatest().toHTML()} )
    }

    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            apiVersion = "1.5"
            languageVersion = "1.5"
            javaParameters = true
        }
    }

    githubReleaseUpload {
        githubRepository.set("nise-nabe/armeria-intellij-plugin")
        val credentials = providers.credentials(PasswordCredentials::class, "GitHub")
        githubToken.set(credentials.map { it.password ?: throw GradleException("GitHubPassword is required") })
        releaseName.set(provider { "v$version" })
        releaseFile.set(buildPlugin.flatMap { it.archiveFile })
    }
}

publishing {
    repositories {
        maven {
            name = "GitHub"
            url = uri("https://maven.pkg.github.com/nise-nabe/armeria-intellij-plugin")
            // set ~/.gradle/gradle.properties
            // gitHubUsername
            // gitHubPassword
            credentials(PasswordCredentials::class)
        }
    }
    publications {
        create<MavenPublication>("plugin") {
            artifact(tasks.getByName("buildPlugin"))
        }
    }
}
