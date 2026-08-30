package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import com.linecorp.intellij.plugins.armeria.test.withTemporaryMainSourceRoot
import com.linecorp.intellij.plugins.armeria.test.withTemporaryTestSourceRoot
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
        configureJavaInMain("Server.builder().http(8080).build();") {
            val call = serverBuilderCall()
            assertNotNull(ArmeriaServerLimitsSupport.highlight(call))
            assertTrue(ArmeriaServerLimitsSupport.missingLimits(call).containsAll(ArmeriaProductionChecklist.SERVER_LIMIT_METHODS))
        }
    }

    @Test
    fun allowsServerBuilderWithAllLimits() {
        configureJavaInMain(
            """
            Server.builder()
                    .maxNumConnections(500)
                    .requestTimeout(null)
                    .maxRequestLength(1048576)
                    .build();
            """.trimIndent(),
        ) {
            assertNull(ArmeriaServerLimitsSupport.highlight(serverBuilderCall()))
        }
    }

    @Test
    fun allowsServerBuilderLimitsOnAssignedVariable() {
        configureJavaInMain(
            """
            ServerBuilder sb = Server.builder();
            sb.maxNumConnections(500);
            sb.requestTimeoutMillis(7000);
            sb.maxRequestLength(1048576);
            sb.build();
            """.trimIndent(),
        ) {
            assertNull(ArmeriaServerLimitsSupport.highlight(serverBuilderCall()))
        }
    }

    @Test
    fun skipsServerBuilderInTestSources() {
        configureJavaInTest("Server.builder().http(8080).build();") {
            assertNull(ArmeriaServerLimitsSupport.highlight(serverBuilderCall()))
        }
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
        configureJavaInTest("""WebClient.builder("https://example.com").build();""") {
            assertNull(ArmeriaClientResilienceSupport.highlight(webClientBuilderCall()))
        }
    }

    @Test
    fun allowsWebClientBuilderWithStaticImportedRetryDecorator() {
        myFixture.withTemporaryMainSourceRoot { mainRoot ->
            val content =
                """
                package example;

                import static com.linecorp.armeria.client.retry.RetryingClient.newDecorator;

                import com.linecorp.armeria.client.WebClient;

                public class Main {
                    public static void main(String[] args) {
                        WebClient.builder("https://example.com")
                                .decorator(newDecorator())
                                .build();
                    }
                }
                """.trimIndent()
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                    val file = mainRoot.createChildData(this, "Main.java")
                    VfsUtil.saveText(file, content)
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.configureFromExistingVirtualFile(virtualFile)
            assertNull(ArmeriaClientResilienceSupport.highlight(webClientBuilderCall()))
        }
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
                    this.group.close();
                }
            }
            """.trimIndent(),
        )
        assertNull(ArmeriaEndpointGroupCloseSupport.highlight(endpointGroupOfCall()))
    }

    @Test
    fun highlightsUnclosedFluentDnsEndpointGroup() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;

            public class Main {
                private final DnsAddressEndpointGroup group =
                        DnsAddressEndpointGroup.builder("example.com", 8080).ttl(1, 5).build();
            }
            """.trimIndent(),
        )
        assertNotNull(ArmeriaEndpointGroupCloseSupport.highlight(endpointGroupBuildCall()))
    }

    @Test
    fun allowsClosedFluentDnsEndpointGroup() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;

            public class Main {
                private final DnsAddressEndpointGroup group =
                        DnsAddressEndpointGroup.builder("example.com", 8080).ttl(1, 5).build();

                public void shutdown() {
                    group.close();
                }
            }
            """.trimIndent(),
        )
        assertNull(ArmeriaEndpointGroupCloseSupport.highlight(endpointGroupBuildCall()))
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

    @Test
    fun skipsAbstractFlagsProvider() {
        myFixture.configureByText(
            "BaseFlagsProvider.java",
            """
            package example;

            import com.linecorp.armeria.common.FlagsProvider;

            public abstract class BaseFlagsProvider implements FlagsProvider {
            }
            """.trimIndent(),
        )
        assertNull(ArmeriaFlagsProviderSpiSupport.highlight(classNamed("BaseFlagsProvider")))
    }

    private fun configureJavaInMain(
        body: String,
        assertions: () -> Unit,
    ) {
        myFixture.withTemporaryMainSourceRoot { root ->
            configureJavaFile(root, body, assertions)
        }
    }

    private fun configureJavaInTest(
        body: String,
        assertions: () -> Unit,
    ) {
        myFixture.withTemporaryTestSourceRoot { root ->
            configureJavaFile(root, body, assertions)
        }
    }

    private fun configureJavaFile(
        root: VirtualFile,
        body: String,
        assertions: () -> Unit,
    ) {
        val content = javaSource(body)
        val virtualFile =
            ApplicationManager.getApplication().runWriteAction<VirtualFile> {
                val file = root.createChildData(this, "Main.java")
                VfsUtil.saveText(file, content)
                file
            }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.configureFromExistingVirtualFile(virtualFile)
        assertions()
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

    private fun serverBuilderCall(): PsiMethodCallExpression = callNamed("builder", "Server")

    private fun webClientBuilderCall(): PsiMethodCallExpression = callNamed("builder", "WebClient")

    private fun clientFactoryBuilderCall(): PsiMethodCallExpression = callNamed("builder", "ClientFactory")

    private fun endpointGroupOfCall(): PsiMethodCallExpression = callNamed("of", "DnsAddressEndpointGroup")

    private fun endpointGroupBuildCall(): PsiMethodCallExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, PsiMethodCallExpression::class.java).first { call ->
            call.methodExpression.referenceName == "build" &&
                ArmeriaJavaInspectionCallChains.qualifierSimpleName(call) != "Server" &&
                ArmeriaJavaInspectionCallChains.qualifierSimpleName(call) != "WebClient" &&
                ArmeriaJavaInspectionCallChains.qualifierSimpleName(call) != "ClientFactory"
        }

    private fun callNamed(
        methodName: String,
        qualifierSimpleName: String,
    ): PsiMethodCallExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, PsiMethodCallExpression::class.java).first { call ->
            call.methodExpression.referenceName == methodName &&
                ArmeriaJavaInspectionCallChains.qualifierSimpleName(call) == qualifierSimpleName
        }

    private fun flagsProviderClass(): PsiClass = classNamed("MyFlagsProvider")

    private fun classNamed(name: String): PsiClass =
        PsiTreeUtil.findChildrenOfType(myFixture.file, PsiClass::class.java).first { it.name == name }
}
