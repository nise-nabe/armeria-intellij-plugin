package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ArmeriaBlockingClientKotlinInspectionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
        myFixture.enableInspections(ArmeriaBlockingClientKotlinInspection())
    }

    fun testWarnsWhenChainedWebClientCallsBlockingRoute() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            public class SlowService {
                @Blocking
                @Get("/slow")
                public String slow() {
                    return "slow";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "SlowServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class SlowServiceTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}

                fun testSlow() {
                    server.webClient().get("/slow")
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)
        assertTrue(ArmeriaJUnitServerExtensionSupport.referencesServerVariable(getCall, "server"))

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
        assertEquals(
            message("inspection.blocking.client.problem", "/slow"),
            holder.results.single().descriptionTemplate,
        )
        assertEquals(ProblemHighlightType.GENERIC_ERROR_OR_WARNING, holder.results.single().highlightType)
    }

    fun testCollectsBlockingRouteAndServerExtensionForKotlinTest() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            public class SlowService {
                @Blocking
                @Get("/slow")
                public String slow() {
                    return "slow";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "SlowServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension
            import com.linecorp.armeria.client.WebClient

            class SlowServiceTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}

                fun testSlow() {
                    WebClient.of(server.httpUri()).get("/slow")
                }
            }
            """.trimIndent(),
        )

        assertTrue(ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(project).contains("/slow"))
        assertEquals(1, ArmeriaJUnitServerExtensionCollector.collect(project).size)
    }

    fun testNoWarningForBlockingWebClient() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            public class SlowService {
                @Blocking
                @Get("/slow")
                public String slow() {
                    return "slow";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "SlowServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class SlowServiceTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}

                fun testSlow() {
                    server.blockingWebClient().get("/slow")
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(0, holder.results.size)
    }

    private fun findGetCall(file: KtFile): KtCallExpression =
        PsiTreeUtil
            .collectElementsOfType(file, KtCallExpression::class.java)
            .first { it.calleeExpression?.text == "get" }
}
