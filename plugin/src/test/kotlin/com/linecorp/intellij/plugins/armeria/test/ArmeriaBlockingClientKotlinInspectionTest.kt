package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ArmeriaBlockingClientKotlinInspectionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
        myFixture.enableInspections(ArmeriaBlockingClientKotlinInspection())
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

        val warnings =
            myFixture.doHighlighting().filter {
                it.description == message("inspection.blocking.client.problem", "/slow")
            }
        assertEquals(0, warnings.size)
    }
}
