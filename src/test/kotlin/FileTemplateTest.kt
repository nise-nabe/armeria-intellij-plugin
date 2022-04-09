package com.linecorp.intellij.plugins.armeria

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.StarterUtils
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.testFramework.TestApplicationManager
import com.linecorp.intellij.plugins.armeria.utils.GeneratorContextForTest
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
internal class FileTemplateTest {
    @TempDir
    private lateinit var testProjectDir: File

    private lateinit var settingsFile: File
    private lateinit var buildFile: File

    private lateinit var ftManager: FileTemplateManager

    @RelaxedMockK
    private lateinit var context: GeneratorContextForTest

    private lateinit var runner: GradleRunner

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle.kts")
        buildFile = testProjectDir.resolve("build.gradle.kts")

        val projectName = "test"
        TestApplicationManager.getInstance()
        val projectOptions = OpenProjectTaskBuilder().projectName(projectName)
        val intellijProject = ProjectManagerEx.getInstanceEx().openProject(testProjectDir.toPath(), projectOptions.build())
        ftManager = FileTemplateManager.getInstance(intellijProject!!)

        settingsFile.writeText(ftManager.getJ2eeTemplate("armeria-settings.gradle.kts").getText(mapOf<String, Any>(
            "context" to mockk<GeneratorContextForTest> {
                every { artifact } returns projectName
            }
        )))

        val pom = FileTemplateTest::class.java.classLoader.getResourceAsStream("starters/armeria.pom").let { JDOMUtil.load(it) }
        val dependencyConfig = StarterUtils.parseDependencyConfig(pom, "", true)
        every { context.getVersion(any(), any()) } answers {
            dependencyConfig.getVersion(arg(0), arg(1))
        }

        runner = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withGradleVersion("7.1.1") // Intellij bundled gradle version
    }

    @Test
    fun test() {
        buildFile.writeText(ftManager.getJ2eeTemplate("armeria-build.gradle.kts").getText(mapOf(
            "context" to context.apply {
                every { hasLanguage("kotlin") } returns true
                every { hasLanguage("scala") } returns false
                every { hasLibrary(any()) } returns true
            }
        )))

        val buildResult = runner.withArguments("help").build()

        val taskResult = buildResult.task(":help")
        assertNotNull(taskResult)
        assertEquals(TaskOutcome.SUCCESS, taskResult.outcome)
    }
}
