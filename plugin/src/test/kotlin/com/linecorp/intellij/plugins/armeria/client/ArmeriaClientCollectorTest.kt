package com.linecorp.intellij.plugins.armeria.client

import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase

class ArmeriaClientCollectorTest : ArmeriaClientFixtureTestBase() {

    fun testCollectWebClientOf() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://example.com");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("HTTP", endpoint.clientType)
        assertEquals("https://example.com", endpoint.uri)
        assertTrue(endpoint.target.contains("WebClient"))
    }

    fun testCollectWebClientBuilderWithUri() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder("https://api.example.com");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("https://api.example.com", endpoints.single().uri)
    }

    fun testCollectGrpcClientsNewClient() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.grpc.GrpcClients;

            public class Main {
                public static void main(String[] args) {
                    GrpcClients.newClient("https://grpc.example.com", MyStub.class);
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class MyStub {
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("gRPC", endpoint.clientType)
        assertEquals("https://grpc.example.com", endpoint.uri)
    }

    fun testCollectThriftClientsNewClient() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.thrift.ThriftClients;

            public class Main {
                public static void main(String[] args) {
                    ThriftClients.newClient("https://thrift.example.com", MyIface.class);
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class MyIface {
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        val endpoint = endpoints.single()
        assertEquals("Thrift", endpoint.clientType)
        assertEquals("https://thrift.example.com", endpoint.uri)
    }

    fun testIgnoresNoArgWebClientBuilder() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder();
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testNoFalsePositiveOnUnrelatedBuilder() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    StringBuilder.builder();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public final class StringBuilder {
                public static StringBuilder builder() {
                    return new StringBuilder();
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertTrue(endpoints.isEmpty())
    }

    fun testCollectWebClientOfViaStaticImport() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import static com.linecorp.armeria.client.WebClient.of;

            public class Main {
                public static void main(String[] args) {
                    of("https://example.com");
                }
            }
            """.trimIndent(),
        )

        val endpoints = ArmeriaClientCollector.collect(project)

        assertEquals(1, endpoints.size)
        assertEquals("WebClient", endpoints.single().target)
    }

    fun testDeduplicatesSameCallSite() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://example.com");
                }
            }
            """.trimIndent(),
        )

        val first = ArmeriaClientCollector.collect(project)
        val second = ArmeriaClientCollector.collect(project)

        assertEquals(1, first.size)
        assertEquals(first, second)
    }

}
