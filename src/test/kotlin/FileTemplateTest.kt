package com.linecorp.intellij.plugins.armeria

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.DependencyConfig
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.StarterUtils
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.testFramework.TestApplicationManager
import io.mockk.every
import io.mockk.mockk
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.debugger.getClassName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class FileTemplateTest {
    @TempDir
    private lateinit var testProjectDir: File

    private lateinit var settingsFile: File
    private lateinit var buildFile: File

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle.kts")
        buildFile = testProjectDir.resolve("build.gradle.kts")
    }

    @Test
    fun test() {
        val projectName = "test"
        TestApplicationManager.getInstance()
        val projectOptions = OpenProjectTaskBuilder().projectName(projectName)
        val intellijProject = ProjectManagerEx.getInstanceEx().openProject(testProjectDir.toPath(), projectOptions.build())
        val ftManager = FileTemplateManager.getInstance(intellijProject!!)

        val pom = FileTemplateTest::class.java.classLoader.getResourceAsStream("starters/armeria.pom").let { JDOMUtil.load(it) }
        val dependencyConfig = StarterUtils.parseDependencyConfig(pom, "", true)
        val template = ftManager.getJ2eeTemplate("armeria-build.gradle.kts")
        buildFile.writeText(template.getText(mapOf(
            "context" to mockk<GeneratorContext>(relaxed = true) {
                every { hasLanguage("kotlin") } returns true
                every { hasLanguage("scala") } returns false
                every { getVersion(any(), any()) } answers {
                    dependencyConfig.getVersion(arg(0), arg(1))
                }
                every { hasLibrary(any()) } returns true
            },
        )))
        settingsFile.writeText(ftManager.getJ2eeTemplate("armeria-settings.gradle.kts").getText(mapOf<String, Any>(
            "context" to mockk<GeneratorContext> {
                every { artifact } returns projectName
            }
        )))

        val buildResult = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withGradleVersion("7.1.1") // Intellij bundled gradle version
            .withArguments("help")
            .build()

        val taskResult = buildResult.task(":help")
        assertNotNull(taskResult)
        assertEquals(TaskOutcome.SUCCESS, taskResult.outcome)
    }

    /** @see  com.intellij.ide.starters.local.GeneratorContext */
    @Suppress("unused")
    interface GeneratorContext {
        val starterId: String
        val moduleName: String
        val group: String
        val artifact: String
        val version: String
        val testRunnerId: String?
        val rootPackage: String
        val sdkVersion: JavaSdkVersion?
        val assets: List<GeneratorAsset>
        val outputDirectory: VirtualFile

        fun hasLanguage(languageId: String): Boolean
        fun hasLibrary(libraryId: String): Boolean
        fun hasAnyLibrary(vararg ids: String): Boolean
        fun hasAllLibraries(vararg ids: String): Boolean
        fun getVersion(group: String, artifact: String): String?
        fun getBomProperty(propertyId: String): String?
        fun getProperty(propertyId: String): String?
        fun asPlaceholder(propertyId: String): String
        fun isSdkAtLeast(version: String): Boolean
        val rootPackagePath: String
        val sdkFeatureVersion: Int
    }
}
