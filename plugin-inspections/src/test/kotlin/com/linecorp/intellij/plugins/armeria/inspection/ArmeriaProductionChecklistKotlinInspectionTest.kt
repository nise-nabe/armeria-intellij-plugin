package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import com.linecorp.intellij.plugins.armeria.test.withTemporaryMainSourceRoot
import com.linecorp.intellij.plugins.armeria.test.withTemporaryTestSourceRoot
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmeriaProductionChecklistKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerProductionChecklistInspectionStubs()
    }

    @Test
    fun highlightsServerBuilderWithoutLimits() {
        configureKotlinInMain("Server.builder().http(8080).build()") {
            val call = serverBuilderCall()
            assertNotNull(ArmeriaServerLimitsKotlinSupport.highlight(call))
            assertTrue(
                ArmeriaServerLimitsKotlinSupport.missingLimits(call).containsAll(ArmeriaProductionChecklist.SERVER_LIMIT_METHODS),
            )
        }
    }

    @Test
    fun allowsServerBuilderApplyLimits() {
        configureKotlinInMain(
            """
            Server.builder().apply {
                maxNumConnections(500)
                requestTimeout(null)
                maxRequestLength(1048576)
            }.build()
            """.trimIndent(),
        ) {
            assertNull(ArmeriaServerLimitsKotlinSupport.highlight(serverBuilderCall()))
        }
    }

    @Test
    fun allowsServerBuilderLimitsOnAssignedApply() {
        configureKotlinInMain(
            """
            val sb = Server.builder()
            sb.apply {
                maxNumConnections(500)
                requestTimeout(null)
                maxRequestLength(1048576)
            }
            """.trimIndent(),
        ) {
            assertNull(ArmeriaServerLimitsKotlinSupport.highlight(serverBuilderCall()))
        }
    }

    @Test
    fun skipsServerBuilderInTestSources() {
        configureKotlinInTest("Server.builder().http(8080).build()") {
            assertNull(ArmeriaServerLimitsKotlinSupport.highlight(serverBuilderCall()))
        }
    }

    @Test
    fun highlightsWebClientBuilderWithoutResilience() {
        configureKotlinInMain("""WebClient.builder("https://example.com").build()""") {
            assertNotNull(ArmeriaClientResilienceKotlinSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun allowsWebClientBuilderWithCircuitBreaker() {
        configureKotlinInMain(
            """
            WebClient.builder("https://example.com")
                .decorator(CircuitBreakerClient.newDecorator())
                .build()
            """.trimIndent(),
        ) {
            assertNull(ArmeriaClientResilienceKotlinSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun skipsWebClientBuilderInTestSources() {
        configureKotlinInTest("""WebClient.builder("https://example.com").build()""") {
            assertNull(ArmeriaClientResilienceKotlinSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun allowsWebClientBuilderWithAssignedApplyDecorator() {
        configureKotlinInMain(
            """
            val client = WebClient.builder("https://example.com")
            client.apply {
                decorator(RetryingClient.newDecorator())
            }
            """.trimIndent(),
        ) {
            assertNull(ArmeriaClientResilienceKotlinSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun highlightsClientFactoryBuiltNextToClient() {
        configureKotlin(
            """
            val factory = ClientFactory.builder().build()
            WebClient.builder("https://example.com").factory(factory).build()
            """.trimIndent(),
        )
        assertNotNull(ArmeriaClientFactoryReuseKotlinSupport.highlight(clientFactoryBuilderCall()))
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
        assertNull(ArmeriaClientFactoryReuseKotlinSupport.highlight(clientFactoryBuilderCall()))
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
        assertNotNull(ArmeriaEndpointGroupCloseKotlinSupport.highlight(endpointGroupOfCall()))
    }

    @Test
    fun allowsEndpointGroupClosedViaThis() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup

            class Main {
                private val group = DnsAddressEndpointGroup.of("example.com", 8080)

                fun shutdown() {
                    this.group.close()
                }
            }
            """.trimIndent(),
        )
        assertNull(ArmeriaEndpointGroupCloseKotlinSupport.highlight(endpointGroupOfCall()))
    }

    @Test
    fun highlightsUnclosedAssignedEndpointGroup() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup

            class Main {
                private lateinit var group: DnsAddressEndpointGroup

                fun start() {
                    group = DnsAddressEndpointGroup.of("example.com", 8080)
                }
            }
            """.trimIndent(),
        )
        assertNotNull(ArmeriaEndpointGroupCloseKotlinSupport.highlight(endpointGroupOfCall()))
    }

    @Test
    fun allowsAssignedEndpointGroupThatIsClosed() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup

            class Main {
                private lateinit var group: DnsAddressEndpointGroup

                fun start() {
                    group = DnsAddressEndpointGroup.of("example.com", 8080)
                }

                fun shutdown() {
                    group.close()
                }
            }
            """.trimIndent(),
        )
        assertNull(ArmeriaEndpointGroupCloseKotlinSupport.highlight(endpointGroupOfCall()))
    }

    @Test
    fun highlightsUnclosedFluentDnsEndpointGroup() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup

            class Main {
                private val group = DnsAddressEndpointGroup.builder("example.com", 8080).ttl(1, 5).build()
            }
            """.trimIndent(),
        )
        assertNotNull(ArmeriaEndpointGroupCloseKotlinSupport.highlight(endpointGroupBuildCall()))
    }

    @Test
    fun allowsEndpointGroupUse() {
        configureKotlin("""DnsAddressEndpointGroup.of("example.com", 8080).use { }""")
        assertNull(ArmeriaEndpointGroupCloseKotlinSupport.highlight(endpointGroupOfCall()))
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
        assertNotNull(ArmeriaFlagsProviderSpiKotlinSupport.highlight(flagsProviderDeclaration()))
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
        assertNull(ArmeriaFlagsProviderSpiKotlinSupport.highlight(flagsProviderDeclaration()))
    }

    @Test
    fun allowsFlagsProviderWithInlineSpiComment() {
        myFixture.addFileToProject(
            "META-INF/services/com.linecorp.armeria.common.FlagsProvider",
            "example.MyFlagsProvider  # ServiceLoader inline comment\n",
        )
        myFixture.configureByText(
            "MyFlagsProvider.kt",
            """
            package example

            import com.linecorp.armeria.common.FlagsProvider

            class MyFlagsProvider : FlagsProvider
            """.trimIndent(),
        )
        assertNull(ArmeriaFlagsProviderSpiKotlinSupport.highlight(flagsProviderDeclaration()))
    }

    @Test
    fun skipsAbstractFlagsProvider() {
        myFixture.configureByText(
            "BaseFlagsProvider.kt",
            """
            package example

            import com.linecorp.armeria.common.FlagsProvider

            abstract class BaseFlagsProvider : FlagsProvider
            """.trimIndent(),
        )
        assertNull(
            ArmeriaFlagsProviderSpiKotlinSupport.highlight(
                PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java).first {
                    it.name == "BaseFlagsProvider"
                },
            ),
        )
    }

    private fun configureKotlinInMain(
        body: String,
        assertions: () -> Unit,
    ) {
        myFixture.withTemporaryMainSourceRoot { root ->
            configureKotlinFile(root, body, assertions)
        }
    }

    private fun configureKotlinInTest(
        body: String,
        assertions: () -> Unit,
    ) {
        myFixture.withTemporaryTestSourceRoot { root ->
            configureKotlinFile(root, body, assertions)
        }
    }

    private fun configureKotlinFile(
        root: VirtualFile,
        body: String,
        assertions: () -> Unit,
    ) {
        val content = kotlinSource(body)
        val virtualFile =
            ApplicationManager.getApplication().runWriteAction<VirtualFile> {
                val file = root.createChildData(this, "Main.kt")
                VfsUtil.saveText(file, content)
                file
            }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.configureFromExistingVirtualFile(virtualFile)
        assertions()
    }

    private fun configureKotlin(body: String) {
        myFixture.configureByText("Main.kt", kotlinSource(body))
    }

    private fun kotlinSource(body: String): String =
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
        """.trimIndent()

    private fun serverBuilderCall(): KtCallExpression = callNamed("builder", "Server")

    private fun webClientBuilderCall(): KtCallExpression = callNamed("builder", "WebClient")

    private fun clientFactoryBuilderCall(): KtCallExpression = callNamed("builder", "ClientFactory")

    private fun endpointGroupOfCall(): KtCallExpression = callNamed("of", "DnsAddressEndpointGroup")

    private fun endpointGroupBuildCall(): KtCallExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, KtCallExpression::class.java).first { call ->
            ArmeriaKotlinInspectionCallChains.callName(call) == "build" &&
                ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call) != "Server" &&
                ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call) != "WebClient" &&
                ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call) != "ClientFactory"
        }

    private fun callNamed(
        methodName: String,
        qualifierSimpleName: String,
    ): KtCallExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, KtCallExpression::class.java).first { call ->
            ArmeriaKotlinInspectionCallChains.callName(call) == methodName &&
                ArmeriaKotlinInspectionCallChains.qualifierSimpleName(call) == qualifierSimpleName
        }

    private fun flagsProviderDeclaration(): KtClassOrObject =
        PsiTreeUtil.findChildrenOfType(myFixture.file, KtClassOrObject::class.java).first {
            it.name == "MyFlagsProvider"
        }
}
