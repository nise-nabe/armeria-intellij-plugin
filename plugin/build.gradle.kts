import org.gradle.api.attributes.Attribute
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.io.File
import java.util.EnumSet
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * IDEA 2026.3 Beta (build 263.7307.44, released 2026-10-22). Installer-only; there is no
 * Maven archive for this build. Pinned so a newer EAP does not replace the check.
 *
 * https://www.jetbrains.com/idea/nextversion/
 * https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides
 */
val idea20263BetaBuild = "263.7307.44"

plugins {
    id("com.linecorp.intellij.platform-plugin")
}

group = "com.linecorp.intellij"
version = providers.gradleProperty("pluginVersion").get()

dependencies {
    intellijPlatform {
        pluginComposedModule(implementation(project(":plugin-shared")))
        pluginComposedModule(implementation(project(":plugin-wizard")))
        pluginComposedModule(implementation(project(":plugin-route-model")))
        pluginComposedModule(implementation(project(":plugin-route-collectors")))
        pluginComposedModule(implementation(project(":plugin-route-spring")))
        pluginComposedModule(implementation(project(":plugin-route-protocol")))
        pluginComposedModule(implementation(project(":plugin-route-analysis")))
        intellijIdeaUltimate(
            libs.versions.idea.platform
                .get(),
        ) {
            // 2026.2.4 is in the IntelliJ Maven repository. The products-feed installer
            // listing still stops at 2026.2.3, so the default installer lookup cannot resolve it.
            useInstaller = false
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.properties")
        bundledPlugin("idea.plugin.protoeditor")
        plugin(
            "org.intellij.scala",
            libs.versions.scala.plugin
                .get(),
        )
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Plugin.Java, configurationName = "testFixturesImplementation")
        testFramework(TestFrameworkType.JUnit5)
    }
    testFixturesImplementation(testFixtures(project(":plugin-route-collectors")))
    testFixturesImplementation(libs.junit4)
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            dependencies {
                implementation(testFixtures(project()))
                implementation(testFixtures(project(":plugin-route-collectors")))
                implementation(libs.velocity.engine.core)
                implementation(libs.junit4)
            }
        }
    }
}

changelog {
    version.set(providers.gradleProperty("pluginVersion"))
    path.set(file("CHANGELOG.md").canonicalPath)
    header.set(provider { "[${version.get()}] - ${date()}" })
    unreleasedTerm.set("[Unreleased]")
    keepUnreleasedSection.set(true)
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

intellijPlatform {
    pluginConfiguration {
        id = "com.linecorp.armeria"
        name = "Armeria"
        version = project.version.toString()
        vendor {
            name = "nise_nabe"
            email = "nise.nabe@gmail.com"
            url = "https://github.com/nise-nabe/armeria-intellij-plugin"
        }
        description =
            """
            IntelliJ IDEA plugin for the Armeria microservice framework.
            Discover annotated services and client calls, inspect duplicate routes,
            generate Armeria projects, and run Armeria servers from the IDE.
            """.trimIndent()
        val changelog = project.changelog
        changeNotes =
            providers.gradleProperty("pluginVersion").map { pluginVersion ->
                with(changelog) {
                    renderItem(
                        (getOrNull(pluginVersion) ?: getUnreleased())
                            .withHeader(false)
                            .withEmptySections(false),
                        Changelog.OutputType.HTML,
                    )
                }
            }
    }
    pluginVerification {
        ides {
            // 2026.2.4, the IDE this build compiles against.
            current()
            // 2026.3 Beta. useInstaller is required: 263.7307.44 is published as an IDE installer,
            // not as a com.jetbrains.intellij.idea:idea Maven artifact.
            create(IntelliJPlatformType.IntellijIdeaUltimate, idea20263BetaBuild) {
                useInstaller = true
            }
        }
        // Gate the release IDE on unloadability and compatibility, not pre-existing
        // internal/experimental APIs (e.g. JavaModuleType in the wizard).
        // NOT_DYNAMIC is the restart-required finding.
        // 2026.3 Beta findings are reported by verifyPlugin20263 and do not use this failure level.
        failureLevel.set(
            EnumSet.of(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
            ),
        )
    }
}

tasks.named<VerifyPluginTask>("verifyPlugin") {
    description =
        "Checks binary compatibility with the compile IDE (2026.2.4). " +
        "2026.3 Beta is verified separately by verifyPlugin20263."
    ides.setFrom(project.pluginVerifierIdeFiles(include20263 = false))
}

tasks.register<VerifyPluginTask>("verifyPlugin20263") {
    group = "verification"
    description =
        "Reports IntelliJ Plugin Verifier results for IDEA 2026.3 Beta $idea20263BetaBuild. " +
        "Compatibility findings do not fail the build; 2026.3 is still Beta."
    val buildPluginTask = tasks.named<BuildPluginTask>("buildPlugin")
    archiveFile.convention(buildPluginTask.flatMap { it.archiveFile })
    ides.setFrom(project.pluginVerifierIdeFiles(include20263 = true))
    // Empty so every verifier category is printed and written to the report, but none fails CI.
    // A missing verdict still fails the task (VerifyPluginTask fails closed).
    failureLevel.set(emptyList())
    verificationReportsDirectory.set(layout.buildDirectory.dir("reports/pluginVerifier20263"))
    verificationReportsFormats.set(
        listOf(
            VerifyPluginTask.VerificationReportsFormats.PLAIN,
            VerifyPluginTask.VerificationReportsFormats.HTML,
            VerifyPluginTask.VerificationReportsFormats.MARKDOWN,
        ),
    )
    externalPrefixes.set(emptyList())
    freeArgs.set(emptyList())
    teamCityOutputFormat.set(false)
    subsystemsToCheck.set(VerifyPluginTask.Subsystems.ALL)
    offline.set(providers.provider { gradle.startParameter.isOffline })
    listIdes.set(false)
    useBundledRuntime.set(true)
    problemsReportFile.set(layout.buildDirectory.file("reports/problems/problems-report-20263.html"))
    mustRunAfter("verifyPlugin")
}

tasks.register("verifyPluginPackaging") {
    group = "verification"
    description =
        "Fails if the plugin ZIP lib/ contains Kotlin stdlib, JetBrains annotations, or sibling module JARs."
    dependsOn("buildPlugin")
    val zipFile =
        layout.buildDirectory.file(
            providers.gradleProperty("pluginVersion").map { version ->
                "distributions/plugin-$version.zip"
            },
        )
    val expectedJarName =
        providers.gradleProperty("pluginVersion").map { version ->
            "plugin-$version.jar"
        }
    inputs.file(zipFile)
    doLast {
        val zipPath = zipFile.get().asFile
        check(zipPath.isFile) { "Plugin ZIP not found: $zipPath" }
        val expectedJar = expectedJarName.get()
        ZipFile(zipPath).use { zip ->
            val jarEntries =
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".jar") }
                    .toList()
            check(jarEntries.size == 1 && jarEntries.single().endsWith("/$expectedJar")) {
                "Expected a single lib/$expectedJar, found $jarEntries in $zipPath"
            }
            val pluginJar =
                checkNotNull(zip.getEntry(jarEntries.single())) {
                    "ZIP entry missing: ${jarEntries.single()}"
                }
            val descriptor =
                zip.getInputStream(pluginJar).use { jarStream ->
                    ZipInputStream(jarStream).use { nested ->
                        generateSequence { nested.nextEntry }
                            .firstOrNull { it.name == "META-INF/plugin.xml" }
                            ?.let { nested.readBytes().toString(Charsets.UTF_8) }
                    }
                }
            check(!descriptor.isNullOrBlank()) {
                "META-INF/plugin.xml missing from $expectedJar"
            }
            check("<id>com.linecorp.armeria</id>" in descriptor) {
                "Patched plugin.xml must use id com.linecorp.armeria"
            }
            check("""require-restart="false"""" in descriptor) {
                "Patched plugin.xml must declare require-restart=\"false\" for dynamic plugin updates"
            }
            val description =
                Regex(
                    "<description(?:\\s[^>]*)?>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</description>",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
                ).find(descriptor)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            check(!description.isNullOrBlank()) {
                "Patched plugin.xml is missing a non-empty <description>"
            }
        }
    }
}

tasks.named("check") {
    // verifyPlugin reuses the compile IDE (2026.2.4). verifyPlugin20263 downloads 2026.3 Beta
    // and reports compatibility problems without failing the build on those findings.
    dependsOn("verifyPluginPackaging", "verifyPlugin", "verifyPlugin20263")
}

private val intellijPlatformExtracted: Attribute<Boolean> =
    Attribute.of("intellijPlatformExtracted", Boolean::class.javaObjectType)

private fun Project.pluginVerifierIdeFiles(include20263: Boolean) =
    files(
        provider {
            val configuration = configurations.getByName("intellijPluginVerifierIdes")
            configuration.incoming.dependencies
                .flatMap { dependency ->
                    configurations
                        .detachedConfiguration(dependency)
                        .apply {
                            attributes.attribute(intellijPlatformExtracted, true)
                        }.resolve()
                }.filter { file ->
                    file.isIdea20263Beta() == include20263
                }
        },
    )

private fun File.isIdea20263Beta(): Boolean {
    val build = intellijPlatformBuildNumber()
    if (build != null) {
        return build == idea20263BetaBuild || build.startsWith("263.")
    }
    return idea20263BetaBuild in path || name.contains("263.7307.44")
}

private fun File.intellijPlatformBuildNumber(): String? {
    val root =
        when {
            isDirectory -> this
            else ->
                runCatching { readText().trim() }
                    .getOrNull()
                    ?.let(::File)
                    ?.takeIf { it.isDirectory }
                    ?: return null
        }
    val info =
        root.resolve("product-info.json").takeIf { it.isFile }
            ?: root.listFiles()?.firstNotNullOfOrNull { child ->
                child.resolve("product-info.json").takeIf { it.isFile }
            }
            ?: return null
    return Regex(""""buildNumber"\s*:\s*"([^"]+)"""")
        .find(info.readText())
        ?.groupValues
        ?.get(1)
}
