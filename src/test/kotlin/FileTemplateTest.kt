package com.linecorp.intellij.plugins.armeria

import com.linecorp.intellij.plugins.armeria.utils.writeKotlin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class FileTemplateTest {
    @TempDir
    lateinit var testProjectDir: File

    private lateinit var settingsFile: File
    private lateinit var buildFile: File

    @BeforeEach
    fun setup() {
        settingsFile = testProjectDir.resolve("settings.gradle.kts")
        buildFile = testProjectDir.resolve("build.gradle.kts")
    }

    @Test
    fun test() {
        buildFile.writeKotlin("""
        """.trimIndent())


        val buildResult = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withGradleVersion("7.1.1") // Intellij bundled gradle version
            .withArguments("help")
            .build()

        val taskResult = buildResult.task(":help")
        assertNotNull(taskResult)
        assertEquals(TaskOutcome.SUCCESS, taskResult.outcome)
    }
}
