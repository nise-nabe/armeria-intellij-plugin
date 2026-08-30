package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaProductionChecklistKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerProductionChecklistInspectionStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(
            ArmeriaServerLimitsKotlinInspection(),
            ArmeriaClientResilienceKotlinInspection(),
            ArmeriaClientFactoryReuseKotlinInspection(),
            ArmeriaEndpointGroupCloseKotlinInspection(),
            ArmeriaFlagsProviderSpiKotlinInspection(),
        )
    }

    @Test
    fun highlightsServerBuilderWithoutLimits() {
        configureKotlin(
            """
            Server.builder().http(8080).build()
            """.trimIndent(),
        )
        assertHighlights(allLimitsMessage(), 1)
    }

    @Test
    fun allowsServerBuilderApplyLimits() {
        configureKotlin(
            """
            Server.builder().apply {
                maxNumConnections(500)
                requestTimeout(null)
                maxRequestLength(1048576)
            }.build()
            """.trimIndent(),
        )
        assertHighlights(allLimitsMessage(), 0)
    }

    @Test
    fun highlightsWebClientBuilderWithoutResilience() {
        configureKotlin(
            """
            WebClient.builder("https://example.com").build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.resilience.problem"), 1)
    }

    @Test
    fun allowsWebClientBuilderWithCircuitBreaker() {
        configureKotlin(
            """
            WebClient.builder("https://example.com")
                .decorator(CircuitBreakerClient.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.resilience.problem"), 0)
    }

    @Test
    fun skipsWebClientBuilderInTestSources() {
        markDefaultSourceRootAsTest()
        configureKotlin(
            """
            WebClient.builder("https://example.com").build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.resilience.problem"), 0)
    }

    @Test
    fun highlightsClientFactoryBuiltNextToClient() {
        configureKotlin(
            """
            val factory = ClientFactory.builder().build()
            WebClient.builder("https://example.com").factory(factory).build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.factory.problem"), 1)
    }

    @Test
    fun allowsSharedClientFactoryProperty() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.ClientFactory
            import com.linecorp.armeria.client.WebClient

            private val factory = ClientFactory.builder().build()

            fun main() {
                WebClient.builder("https://example.com").factory(factory).build()
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.client.factory.problem"), 0)
    }

    @Test
    fun highlightsUnclosedDnsEndpointGroupProperty() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup

            class Main {
                private val group = DnsAddressEndpointGroup.of("example.com", 8080)
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.endpoint.group.close.problem"), 1)
    }

    @Test
    fun allowsEndpointGroupUse() {
        configureKotlin(
            """
            DnsAddressEndpointGroup.of("example.com", 8080).use { }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.endpoint.group.close.problem"), 0)
    }

    @Test
    fun highlightsFlagsProviderWithoutSpiFile() {
        myFixture.configureByText(
            "MyFlagsProvider.kt",
            """
            package example

            import com.linecorp.armeria.common.FlagsProvider

            class MyFlagsProvider : FlagsProvider
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
            "MyFlagsProvider.kt",
            """
            package example

            import com.linecorp.armeria.common.FlagsProvider

            class MyFlagsProvider : FlagsProvider
            """.trimIndent(),
        )
        assertHighlights(message("inspection.production.flags.provider.problem"), 0)
    }

    private fun configureKotlin(body: String) {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.ClientFactory
            import com.linecorp.armeria.client.WebClient
            import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerClient
            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup
            import com.linecorp.armeria.client.retry.RetryingClient
            import com.linecorp.armeria.server.Server

            fun main() {
                $body
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
