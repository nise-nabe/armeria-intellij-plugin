package com.linecorp.intellij.plugins.armeria.run

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmeriaServerListenPortSupportTest : ArmeriaFixtureTestBase() {
    fun testExtractsHttpPortFromJavaMain() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                            .http(8080)
                            .serviceUnder("/docs", new DocService())
                            .build();
                }
            }
            """.trimIndent(),
        )

        val listen = ArmeriaServerListenPortSupport.extractFromFile(myFixture.file)

        assertEquals(ArmeriaListenEndpoint(8080, https = false), listen)
    }

    fun testPrefersHttpOverHttps() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().http(8080).https(8443).build();
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            ArmeriaListenEndpoint(8080, https = false),
            ArmeriaServerListenPortSupport.extractFromFile(myFixture.file),
        )
    }

    fun testExtractsHttpsWhenHttpIsAbsent() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().https(8443).build();
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            ArmeriaListenEndpoint(8443, https = true),
            ArmeriaServerListenPortSupport.extractFromFile(myFixture.file),
        )
    }

    fun testExtractsPortMethod() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().port(9090).build();
                }
            }
            """.trimIndent(),
        )

        assertEquals(
            ArmeriaListenEndpoint(9090, https = false),
            ArmeriaServerListenPortSupport.extractFromFile(myFixture.file),
        )
    }

    fun testExtractsKotlinHttpPort() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().http(8080).build()
            }
            """.trimIndent(),
        )

        assertEquals(
            ArmeriaListenEndpoint(8080, https = false),
            ArmeriaServerListenPortSupport.extractFromFile(myFixture.file),
        )
    }

    fun testExtractsKotlinHttpPortFromApplyBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().apply {
                    http(8080)
                }.build()
            }
            """.trimIndent(),
        )

        assertEquals(
            ArmeriaListenEndpoint(8080, https = false),
            ArmeriaServerListenPortSupport.extractFromFile(myFixture.file),
        )
    }

    fun testExtractsKotlinHttpPortFromApplyOnBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val sb = Server.builder()
                sb.apply {
                    http(9090)
                }.build()
            }
            """.trimIndent(),
        )

        assertEquals(
            ArmeriaListenEndpoint(9090, https = false),
            ArmeriaServerListenPortSupport.extractFromFile(myFixture.file),
        )
    }

    fun testMissingPortReturnsNull() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().service("/hello", (ctx, req) -> null).build();
                }
            }
            """.trimIndent(),
        )

        assertNull(ArmeriaServerListenPortSupport.extractFromFile(myFixture.file))
    }

    fun testIgnoresNonServerHttpCalls() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    Client.http(8080);
                }

                static class Client {
                    static void http(int port) {}
                }
            }
            """.trimIndent(),
        )

        assertNull(ArmeriaServerListenPortSupport.extractFromFile(myFixture.file))
    }

    fun testLaunchInfoBuildsDocServiceUrlFromHttpAndServiceUnder() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                            .http(8080)
                            .serviceUnder("/docs", new DocService())
                            .build();
                }
            }
            """.trimIndent(),
        )

        val module = myFixture.module
        val urls = ArmeriaRunLaunchInfo.resolve(project, module, "example.Main")

        assertEquals("http://127.0.0.1:8080/docs/", urls.docService)
        assertEquals(8080, urls.listen?.port)
    }

    fun testLaunchInfoUsesSpringPortWhenMainHasNoHttp() {
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
        myFixture.addFileToProject(
            "application.properties",
            """
            armeria.ports[0].port=8080
            armeria.ports[0].protocols[0]=http
            armeria.internal-services.include=docs,health,metrics
            armeria.docs-path=/docs
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("spring");
                }
            }
            """.trimIndent(),
        )

        val urls = ArmeriaRunLaunchInfo.resolve(project, myFixture.module, "example.Main")

        assertEquals(8080, urls.listen?.port)
        assertEquals("http://127.0.0.1:8080/docs/", urls.docService)
        assertEquals("http://127.0.0.1:8080/internal/healthcheck", urls.health)
        assertEquals("http://127.0.0.1:8080/internal/metrics", urls.metrics)
    }

    fun testLaunchInfoPrefersDefaultApplicationPortOverProfile() {
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
        myFixture.addFileToProject(
            "application-prod.properties",
            """
            armeria.ports[0].port=9999
            armeria.ports[0].protocols[0]=http
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "application.properties",
            """
            armeria.ports[0].port=8080
            armeria.ports[0].protocols[0]=http
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("spring");
                }
            }
            """.trimIndent(),
        )

        val urls = ArmeriaRunLaunchInfo.resolve(project, myFixture.module, "example.Main")

        assertEquals(8080, urls.listen?.port)
    }

    fun testLaunchInfoMissingPortDoesNotFail() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("no server");
                }
            }
            """.trimIndent(),
        )

        val urls = ArmeriaRunLaunchInfo.resolve(project, myFixture.module, "example.Main")

        assertNull(urls.listen)
        assertNull(urls.docService)
        assertEquals(true, urls.isEmpty)
    }
}
