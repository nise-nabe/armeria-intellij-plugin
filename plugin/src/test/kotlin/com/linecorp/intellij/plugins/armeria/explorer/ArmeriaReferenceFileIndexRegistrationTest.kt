package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaReferenceFileIndex
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaReferenceFileIndexRegistrationTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testSpringIndicatorFilesAreIncludedOnlyWhenRequested() {
        val file =
            myFixture
                .configureByText(
                    "ArmeriaConfig.kt",
                    """
                    package example

                    class ArmeriaConfig {
                        fun armeriaServerConfigurator(): ArmeriaServerConfigurator? = null
                    }
                    """.trimIndent(),
                ).virtualFile
        val scope = GlobalSearchScope.projectScope(project)
        FileBasedIndex.getInstance().ensureUpToDate(ArmeriaReferenceFileIndex.NAME, project, scope)

        val indexedFiles = ArmeriaReferenceFileIndex.findFiles(project, scope)
        assertFalse(file in indexedFiles)
        val springIndicatorFiles =
            ArmeriaReferenceFileIndex.findFiles(
                project,
                scope,
                includeSpringBootArmeriaIndicators = true,
            )
        assertTrue(file in springIndicatorFiles)
    }
}
