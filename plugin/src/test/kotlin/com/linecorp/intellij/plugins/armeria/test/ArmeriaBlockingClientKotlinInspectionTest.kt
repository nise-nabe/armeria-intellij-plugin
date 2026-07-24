package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
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

    fun testWarnsWhenLocalWebClientCallsBlockingRoute() {
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
                    val client = server.webClient()
                    client.get("/slow")
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
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

    fun testNoWarningForUnrelatedWebClientOf() {
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
                    WebClient.of("http://localhost:0").get("/slow")
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

    fun testWarnsWhenFullyQualifiedWebClientOfCallsBlockingRoute() {
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
                    com.linecorp.armeria.client.WebClient.of(server.httpUri()).get("/slow")
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
    }

    fun testWarnsWhenNestedTestUsesOuterServerExtension() {
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

                inner class Nested {
                    fun testSlow() {
                        server.webClient().get("/slow")
                    }
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
    }

    fun testWarnsWhenSubclassUsesInheritedServerExtension() {
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
            "UserServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            abstract class BaseIntegrationTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}
            }

            class UserServiceTest : BaseIntegrationTest() {
                fun testSlow() {
                    server.webClient().get("/slow")
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
    }

    fun testWarnsWhenCompanionObjectServerExtensionUsesAsyncWebClient() {
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
                companion object {
                    @RegisterExtension
                    @JvmField
                    val server: ServerExtension = object : ServerExtension() {}
                }

                fun testSlow() {
                    server.webClient().get("/slow")
                }
            }
            """.trimIndent(),
        )
        val getCall = findGetCall(myFixture.file as KtFile)

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
    }

    fun testNoWarningInProductionFileWithoutRegisterExtension() {
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
            "SlowService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Blocking
            import com.linecorp.armeria.server.annotation.Get

            class SlowService {
                @Blocking
                @Get("/slow")
                fun slow(): String = "slow"
            }
            """.trimIndent(),
        )

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientKotlinInspection().buildVisitor(holder, false)
        assertTrue(visitor === PsiElementVisitor.EMPTY_VISITOR)
    }

    private fun findGetCall(file: KtFile): KtCallExpression =
        PsiTreeUtil
            .collectElementsOfType(file, KtCallExpression::class.java)
            .first { it.calleeExpression?.text == "get" }
}
