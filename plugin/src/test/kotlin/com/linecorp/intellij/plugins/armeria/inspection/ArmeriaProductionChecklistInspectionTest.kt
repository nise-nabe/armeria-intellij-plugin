package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaProductionChecklistInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerProductionChecklistInspectionStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(
            ArmeriaServerLimitsInspection(),
            ArmeriaClientResilienceInspection(),
            ArmeriaClientFactoryReuseInspection(),
            ArmeriaEndpointGroupCloseInspection(),
            ArmeriaFlagsProviderSpiInspection(),
        )
    }

    @Test
    fun highlightsServerBuilderWithoutLimits() {
        configureJava(
            """
            Server.builder().http(8080).build();
            """.trimIndent(),
        )
        assertHighlights(allLimitsMessage(), 1)
    }

    @Test
    fun allowsServerBuilderWithAllLimits() {
        configureJava(
            """
            Server.builder()
                    .maxNumConnections(500)
                    .requestTimeout(null)
                    .maxRequestLength(1048576)
                    .build();
            """.trimIndent(),
        )
        assertHighlights(allLimitsMessage(), 0)
    }

    @Test
    fun allowsServerBuilderLimitsOnAssignedVariable() {
        configureJava(
            """
            ServerBuilder sb = Server.builder();
            sb.maxNumConnections(500);
            sb.requestTimeoutMillis(7000);
            sb.maxRequestLength(1048576);
            sb.build();
            """.trimIndent(),
        )
        assertHighlights(allLimitsMessage(), 0)
    }

    @Test
    fun highlightsWebClientBuilderWithoutResilience() {
        configureJava(
            """
            WebClient.builder("https://example.com").build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.resilience.problem"), 1)
    }

    @Test
    fun allowsWebClientBuilderWithRetryingClient() {
        configureJava(
            """
            WebClient.builder("https://example.com")
                    .decorator(RetryingClient.newDecorator())
                    .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.resilience.problem"), 0)
    }

    @Test
    fun skipsWebClientBuilderInTestSources() {
        markDefaultSourceRootAsTest()
        configureJava(
            """
            WebClient.builder("https://example.com").build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.resilience.problem"), 0)
    }

    @Test
    fun highlightsClientFactoryBuiltNextToClient() {
        configureJava(
            """
            ClientFactory factory = ClientFactory.builder().build();
            WebClient.builder("https://example.com").factory(factory).build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.factory.problem"), 1)
    }

    @Test
    fun allowsSharedClientFactoryField() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.ClientFactory;
            import com.linecorp.armeria.client.WebClient;

            public class Main {
                private static final ClientFactory FACTORY = ClientFactory.builder().build();

                public static void main(String[] args) {
                    WebClient.builder("https://example.com").factory(FACTORY).build();
                }
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.factory.problem"), 0)
    }

    @Test
    fun highlightsUnclosedDnsEndpointGroupField() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;

            public class Main {
                private final DnsAddressEndpointGroup group = DnsAddressEndpointGroup.of("example.com", 8080);
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.endpoint.group.close.problem"), 1)
    }

    @Test
    fun allowsTryWithResourcesEndpointGroup() {
        configureJava(
            """
            try (DnsAddressEndpointGroup group = DnsAddressEndpointGroup.of("example.com", 8080)) {
                group.close();
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.endpoint.group.close.problem"), 0)
    }

    @Test
    fun allowsEndpointGroupFieldThatIsClosed() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;

            public class Main {
                private final DnsAddressEndpointGroup group = DnsAddressEndpointGroup.of("example.com", 8080);

                public void shutdown() {
                    group.close();
                }
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.endpoint.group.close.problem"), 0)
    }

    @Test
    fun highlightsFlagsProviderWithoutSpiFile() {
        myFixture.configureByText(
            "MyFlagsProvider.java",
            """
            package example;

            import com.linecorp.armeria.common.FlagsProvider;

            public class MyFlagsProvider implements FlagsProvider {
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.flags.provider.problem"), 1)
    }

    @Test
    fun allowsFlagsProviderWithSpiFile() {
        myFixture.addFileToProject(
            "META-INF/services/com.linecorp.armeria.common.FlagsProvider",
            "example.MyFlagsProvider\n",
        )
        myFixture.configureByText(
            "MyFlagsProvider.java",
            """
            package example;

            import com.linecorp.armeria.common.FlagsProvider;

            public class MyFlagsProvider implements FlagsProvider {
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.flags.provider.problem"), 0)
    }

    private fun configureJava(body: String) {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.ClientFactory;
            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerClient;
            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
            import com.linecorp.armeria.client.retry.RetryingClient;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;

            public class Main {
                public static void main(String[] args) {
                    $body
                }
            }
            """.trimIndent(),
        )
    }

    private fun markDefaultSourceRootAsTest() {
        val contentRoot = ModuleRootManager.getInstance(module).contentRoots.first()
        PsiTestUtil.removeSourceRoot(module, contentRoot)
        PsiTestUtil.addSourceRoot(module, contentRoot, true)
    }

    private fun allLimitsMessage(): String =
        message(
            "inspection.production.server.limits.problem",
            "maxNumConnections, requestTimeout, maxRequestLength",
        )

    private fun assertHighlights(
        expected: String,
        count: Int,
    ) {
        val highlights =
            myFixture.doHighlighting(HighlightSeverity.INFORMATION).filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
