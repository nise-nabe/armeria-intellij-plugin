package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import com.linecorp.intellij.plugins.armeria.test.withTemporaryMainSourceRoot
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
        configureKotlin("Server.builder().http(8080).build()")
        val call = serverBuilderCall()
        assertNotNull(ArmeriaServerLimitsKotlinSupport.highlight(call))
        assertTrue(
            ArmeriaServerLimitsKotlinSupport.missingLimits(call).containsAll(ArmeriaProductionChecklist.SERVER_LIMIT_METHODS),
        )
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
        assertNull(ArmeriaServerLimitsKotlinSupport.highlight(serverBuilderCall()))
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
        markDefaultSourceRootAsTest()
        configureKotlin("""WebClient.builder("https://example.com").build()""")
        assertNull(ArmeriaClientResilienceKotlinSupport.highlight(webClientBuilderCall()))
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

    private fun configureKotlinInMain(
        body: String,
        assertions: () -> Unit,
    ) {
        myFixture.withTemporaryMainSourceRoot { mainRoot ->
            val content = kotlinSource(body)
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                    val file = mainRoot.createChildData(this, "Main.kt")
                    VfsUtil.saveText(file, content)
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.configureFromExistingVirtualFile(virtualFile)
            assertions()
        }
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

    private fun markDefaultSourceRootAsTest() {
        val contentRoot = ModuleRootManager.getInstance(module).contentRoots.first()
        PsiTestUtil.removeSourceRoot(module, contentRoot)
        PsiTestUtil.addSourceRoot(module, contentRoot, true)
    }

    private fun serverBuilderCall(): KtCallExpression = callNamed("builder", "Server")

    private fun webClientBuilderCall(): KtCallExpression = callNamed("builder", "WebClient")

    private fun clientFactoryBuilderCall(): KtCallExpression = callNamed("builder", "ClientFactory")

    private fun endpointGroupOfCall(): KtCallExpression = callNamed("of", "DnsAddressEndpointGroup")

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
