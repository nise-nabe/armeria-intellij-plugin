package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import com.linecorp.intellij.plugins.armeria.test.withTemporaryMainSourceRoot
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmeriaProductionChecklistInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerProductionChecklistInspectionStubs()
    }

    @Test
    fun highlightsServerBuilderWithoutLimits() {
        configureJava("Server.builder().http(8080).build();")
        val call = serverBuilderCall()
        assertNotNull(ArmeriaServerLimitsSupport.highlight(call))
        assertTrue(ArmeriaServerLimitsSupport.missingLimits(call).containsAll(ArmeriaProductionChecklist.SERVER_LIMIT_METHODS))
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
        assertNull(ArmeriaServerLimitsSupport.highlight(serverBuilderCall()))
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
        assertNull(ArmeriaServerLimitsSupport.highlight(serverBuilderCall()))
    }

    @Test
    fun highlightsWebClientBuilderWithoutResilience() {
        configureJavaInMain("""WebClient.builder("https://example.com").build();""") {
            assertNotNull(ArmeriaClientResilienceSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun allowsWebClientBuilderWithRetryingClient() {
        configureJavaInMain(
            """
            WebClient.builder("https://example.com")
                    .decorator(RetryingClient.newDecorator())
                    .build();
            """.trimIndent(),
        ) {
            assertNull(ArmeriaClientResilienceSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun skipsWebClientBuilderInTestSources() {
        markDefaultSourceRootAsTest()
        configureJava("""WebClient.builder("https://example.com").build();""")
        assertNull(ArmeriaClientResilienceSupport.highlight(webClientBuilderCall()))
    }

    @Test
    fun highlightsClientFactoryBuiltNextToClient() {
        configureJava(
            """
            ClientFactory factory = ClientFactory.builder().build();
            WebClient.builder("https://example.com").factory(factory).build();
            """.trimIndent(),
        )
        assertNotNull(ArmeriaClientFactoryReuseSupport.highlight(clientFactoryBuilderCall()))
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
        assertNull(ArmeriaClientFactoryReuseSupport.highlight(clientFactoryBuilderCall()))
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
        assertNotNull(ArmeriaEndpointGroupCloseSupport.highlight(endpointGroupOfCall()))
    }

    @Test
    fun allowsTryWithResourcesEndpointGroup() {
        configureJava(
            """
            try (DnsAddressEndpointGroup group = DnsAddressEndpointGroup.of("example.com", 8080)) {
            }
            """.trimIndent(),
        )
        assertNull(ArmeriaEndpointGroupCloseSupport.highlight(endpointGroupOfCall()))
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
        assertNull(ArmeriaEndpointGroupCloseSupport.highlight(endpointGroupOfCall()))
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
        assertNotNull(ArmeriaFlagsProviderSpiSupport.highlight(flagsProviderClass()))
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
        assertNull(ArmeriaFlagsProviderSpiSupport.highlight(flagsProviderClass()))
    }

    private fun configureJavaInMain(
        body: String,
        assertions: () -> Unit,
    ) {
        myFixture.withTemporaryMainSourceRoot { mainRoot ->
            val content = javaSource(body)
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                    val file = mainRoot.createChildData(this, "Main.java")
                    VfsUtil.saveText(file, content)
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.configureFromExistingVirtualFile(virtualFile)
            assertions()
        }
    }

    private fun configureJava(body: String) {
        myFixture.configureByText("Main.java", javaSource(body))
    }

    private fun javaSource(body: String): String =
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
        """.trimIndent()

    private fun markDefaultSourceRootAsTest() {
        val contentRoot = ModuleRootManager.getInstance(module).contentRoots.first()
        PsiTestUtil.removeSourceRoot(module, contentRoot)
        PsiTestUtil.addSourceRoot(module, contentRoot, true)
    }

    private fun serverBuilderCall(): PsiMethodCallExpression = callNamed("builder", "Server")

    private fun webClientBuilderCall(): PsiMethodCallExpression = callNamed("builder", "WebClient")

    private fun clientFactoryBuilderCall(): PsiMethodCallExpression = callNamed("builder", "ClientFactory")

    private fun endpointGroupOfCall(): PsiMethodCallExpression = callNamed("of", "DnsAddressEndpointGroup")

    private fun callNamed(
        methodName: String,
        qualifierSimpleName: String,
    ): PsiMethodCallExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, PsiMethodCallExpression::class.java).first { call ->
            call.methodExpression.referenceName == methodName &&
                ArmeriaJavaInspectionCallChains.qualifierSimpleName(call) == qualifierSimpleName
        }

    private fun flagsProviderClass(): PsiClass =
        PsiTreeUtil.findChildrenOfType(myFixture.file, PsiClass::class.java).first { it.name == "MyFlagsProvider" }
}
