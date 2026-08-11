package com.linecorp.intellij.plugins.armeria.test

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.message
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaBlockingClientInspectionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
        myFixture.enableInspections(ArmeriaBlockingClientInspection())
    }

    fun testWarnsWhenAsyncWebClientCallsBlockingRoute() {
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                void testSlow() {
                    server.webClient().<warning descr="Route /slow is marked @Blocking; use blockingWebClient() in tests instead of async WebClient.">get</warning>("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                void testSlow() {
                    server.blockingWebClient().get("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;
            import com.linecorp.armeria.client.WebClient;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                void testSlow() {
                    WebClient.of("http://localhost:0").get("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    fun testWarnsWhenWebClientOfServerHttpUriCallsBlockingRoute() {
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;
            import com.linecorp.armeria.client.WebClient;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                void testSlow() {
                    WebClient.of(server.httpUri()).<warning descr="Route /slow is marked @Blocking; use blockingWebClient() in tests instead of async WebClient.">get</warning>("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
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
            "ProductionService.java",
            """
            package example;

            public class ProductionService {
                public void run() {}
            }
            """.trimIndent(),
        )

        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientInspection().buildVisitor(holder, false)
        assertTrue(visitor === PsiElementVisitor.EMPTY_VISITOR)
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
            "InheritedServerBlockingClientTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            abstract class BlockingClientBaseTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};
            }

            public class InheritedServerBlockingClientTest extends BlockingClientBaseTest {
                void testSlow() {
                    server.webClient().<warning descr="Route /slow is marked @Blocking; use blockingWebClient() in tests instead of async WebClient.">get</warning>("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    fun testBlockingRoutePathsReusesRouteCollectorCache() {
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

        ArmeriaRouteAnalysisCollector.collect(project)
        ArmeriaRouteAnalysisCollector.collect(project)
        assertEquals(0, ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned)

        val blockingPaths = ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(project)
        assertTrue(blockingPaths.contains("/slow"))
        val secondPaths = ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(project)
        assertEquals(blockingPaths, secondPaths)
        assertEquals(0, ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned)
    }

    fun testBlockingRoutePathsInvalidateAfterBlockingAnnotationEdit() {
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            public class SlowService {
                @Get("/slow")
                public String slow() {
                    return "slow";
                }
            }
            """.trimIndent(),
        )

        ArmeriaRouteAnalysisCollector.collect(project)
        ArmeriaRouteAnalysisCollector.collect(project)
        assertFalse(ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(project).contains("/slow"))

        myFixture.configureByText(
            "SlowService.java",
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

        val blockingPaths = ArmeriaBlockingClientInspectionPaths.blockingRoutePaths(project)
        assertTrue(blockingPaths.contains("/slow"))
        assertTrue(ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned > 0)
    }

    fun testNoInspectionSetupForMainSourceTestNamedFile() {
        myFixture.withTemporaryMainSourceRoot { mainRoot ->
            val content =
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class MisnamedTest {
                    @RegisterExtension
                    static ServerExtension server = new ServerExtension() {};

                    void run() {
                        server.webClient().get("/slow");
                    }
                }
                """.trimIndent()
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                    val file = mainRoot.createChildData(this, "MisnamedTest.java")
                    VfsUtil.saveText(file, content)
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!

            val manager = InspectionManager.getInstance(project)
            val holder = ProblemsHolder(manager, psiFile, false)
            val visitor = ArmeriaBlockingClientInspection().buildVisitor(holder, false)
            assertTrue(visitor === PsiElementVisitor.EMPTY_VISITOR)
        }
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                class Nested {
                    void testSlow() {
                        server.webClient().<warning descr="Route /slow is marked @Blocking; use blockingWebClient() in tests instead of async WebClient.">get</warning>("/slow");
                    }
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    fun testWarnsWhenStaticNestedTestUsesOuterServerExtension() {
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                static class Nested {
                    void testSlow() {
                        server.webClient().<warning descr="Route /slow is marked @Blocking; use blockingWebClient() in tests instead of async WebClient.">get</warning>("/slow");
                    }
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    fun testNoWarningWhenMultipleRegisterExtensionsAreAmbiguous() {
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
            "AmbiguousTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class AmbiguousTest {
                @RegisterExtension
                static ServerExtension server1 = new ServerExtension() {};

                @RegisterExtension
                static ServerExtension server2 = new ServerExtension() {};

                void testSlow() {
                    server1.webClient().get("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    fun testWarnsWhenAsyncWebClientUsesStaticFinalPathConstant() {
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class SlowServiceTest {
                static final String SLOW_PATH = "/slow";

                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};

                void testSlow() {
                    server.webClient().get(SLOW_PATH);
                }
            }
            """.trimIndent(),
        )

        val getCall =
            PsiTreeUtil
                .collectElementsOfType(myFixture.file, com.intellij.psi.PsiMethodCallExpression::class.java)
                .first { it.methodExpression.referenceName == "get" }
        val manager = InspectionManager.getInstance(project)
        val holder = ProblemsHolder(manager, myFixture.file, false)
        val visitor = ArmeriaBlockingClientInspection().buildVisitor(holder, false)
        getCall.accept(visitor)
        assertEquals(1, holder.results.size)
        assertEquals(
            message("inspection.blocking.client.problem", "/slow"),
            holder.results.single().descriptionTemplate,
        )
    }

    fun testWarnsWhenAsyncWebClientUsesFactoryMethodExtension() {
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
            "SlowServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class SlowServiceTest {
                @RegisterExtension
                static ServerExtension server() {
                    return new ServerExtension() {};
                }

                void testSlow() {
                    server().webClient().<warning descr="Route /slow is marked @Blocking; use blockingWebClient() in tests instead of async WebClient.">get</warning>("/slow");
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }
}
