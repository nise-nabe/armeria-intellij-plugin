package com.linecorp.intellij.plugins.armeria

import com.intellij.ide.starters.local.StarterUtils
import com.intellij.openapi.util.JDOMUtil
import com.linecorp.intellij.plugins.armeria.junit5.IntellijProject
import com.linecorp.intellij.plugins.armeria.junit5.IntellijProjectExtension
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class, IntellijProjectExtension::class)
internal class FileTemplateTest {
    private lateinit var project: IntellijProject

    @RelaxedMockK
    private lateinit var context: GeneratorContextForTest

    private lateinit var runner: GradleRunner

    @BeforeEach
    fun setup() {
        project.settingsFile.writeText(project.ftManager.getJ2eeTemplate("armeria-settings.gradle.kts").getText(mapOf<String, Any>(
            "context" to mockk<GeneratorContextForTest> {
                every { artifact } returns "test"
            }
        )))

        val pom = FileTemplateTest::class.java.classLoader.getResourceAsStream("starters/armeria.pom").let { JDOMUtil.load(it) }
        val dependencyConfig = StarterUtils.parseDependencyConfig(pom, "", true)
        every { context.getVersion(any(), any()) } answers {
            dependencyConfig.getVersion(arg(0), arg(1))
        }

        runner = GradleRunner.create()
            .withProjectDir(project.testProjectDir)
            .withGradleVersion("7.1.1") // Intellij bundled gradle version
    }

    @Test
    fun test() {
        project.buildFile.writeText(project.ftManager.getJ2eeTemplate("armeria-build.gradle.kts").getText(mapOf(
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
