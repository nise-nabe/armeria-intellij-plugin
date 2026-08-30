package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArmeriaMissingDocServiceInspectionTest : ArmeriaFixtureTestBase5() {
    @Test
    fun highlightsAnnotatedServiceWithoutDocService() {
        configureMain(
            """
            Server.builder()
                    .annotatedService(new HelloService())
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    @Test
    fun allowsAnnotatedServiceWhenDocServiceIsMounted() {
        configureMain(
            """
            Server.builder()
                    .annotatedService(new HelloService())
                    .service("/docs", new DocService())
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = false)
    }

    @Test
    fun allowsAnnotatedServiceWhenDocServiceBuilderChainIsMounted() {
        configureMain(
            """
            Server.builder()
                    .annotatedService(new HelloService())
                    .service("/docs", DocService.builder().build())
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = false)
    }

    @Test
    fun highlightsGrpcServiceWithoutDocService() {
        configureMain(
            """
            Server.builder()
                    .service(GrpcService.builder().build())
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    @Test
    fun highlightsWhenDocServiceIsBuiltButNotMounted() {
        configureMain(
            """
            DocService.builder().build();
            Server.builder()
                    .annotatedService(new HelloService())
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    @Test
    fun allowsAssignedDocServicePassedToService() {
        configureMain(
            """
            DocService docs = DocService.builder().build();
            Server.builder()
                    .annotatedService(new HelloService())
                    .service("/docs", docs)
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = false)
    }

    @Test
    fun highlightsWhenMountedServiceVariablesCycle() {
        configureMain(
            """
            Object first = second;
            Object second = first;
            Server.builder()
                    .annotatedService(new HelloService())
                    .service("/x", first)
                    .build();
            """.trimIndent(),
        )
        assertHighlighted(expected = true)
    }

    private fun configureMain(body: String) {
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;
            import com.linecorp.armeria.server.grpc.GrpcService;

            public class Main {
                public static void main(String[] args) {
                    $body
                }
            }
            """.trimIndent(),
        )
    }

    private fun assertHighlighted(expected: Boolean) {
        val method =
            PsiTreeUtil.findChildrenOfType(myFixture.file, PsiMethod::class.java).single { it.name == "main" }
        val highlight = ArmeriaMissingDocServiceSupport.highlight(method)
        if (expected) {
            assertNotNull(highlight, "expected a missing-DocService highlight")
        } else {
            assertNull(highlight, "did not expect a missing-DocService highlight")
        }
    }
}
