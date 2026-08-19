package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaMissingDocServiceInspectionTest : ArmeriaFixtureTestBase5() {
    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaMissingDocServiceInspection())
    }

    @Test
    fun highlightsAnnotatedServiceWithoutDocService() {
        configureMain(
            """
            Server.builder()
                    .annotatedService(new HelloService())
                    .build();
            """.trimIndent(),
        )
        assertHighlights(1)
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
        assertHighlights(0)
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
        assertHighlights(1)
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

    private fun assertHighlights(count: Int) {
        val expected = message("inspection.missing.docservice.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
