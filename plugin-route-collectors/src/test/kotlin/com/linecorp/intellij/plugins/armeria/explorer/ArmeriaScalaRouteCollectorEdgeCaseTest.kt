package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaScalaRouteCollectorEdgeCaseTest : ArmeriaFixtureTestBase() {
    fun testNoFalsePositiveForUnrelatedServiceCall() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .build()
              Client.service("/oops", new Handler())
            }

            object Client {
              def service(path: String, handler: Any): Unit = {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForMyServerBuilderCall() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder().build()
              MyServer.builder()
                .service("/oops", new Handler())
                .build()
            }

            object MyServer {
              def builder(): FakeBuilder = new FakeBuilder()
            }

            class FakeBuilder {
              def service(path: String, handler: Any): FakeBuilder = this
              def build(): Unit = {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForNonArmeriaQualifiedServerBuilder() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder().build()
              com.foo.Server.builder()
                .service("/oops", new Handler())
                .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.foo;

            public final class Server {
                public static FakeBuilder builder() {
                    return null;
                }
            }

            class FakeBuilder {
                public FakeBuilder service(String path, Object handler) {
                    return this;
                }

                public void build() {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testCollectServiceRegistrationNavigatesToServiceLine() {
        val source =
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .service("/api", new HelloService())
                .build()
            }
            """.trimIndent()
        myFixture.configureByText("Main.scala", source)
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoute = routes.firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        val offset = serviceRoute!!.sourceOffset
        assertNotNull(offset)
        val document =
            PsiDocumentManager
                .getInstance(project)
                .getDocument(myFixture.file)
        assertNotNull(document)
        val line = document!!.getLineNumber(offset!!)
        val lineText = document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
        assertTrue(lineText.contains(".service"))
    }
}
