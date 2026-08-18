package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaServerDecoratorKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerServerDecoratorInspectionStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaServerDecoratorKotlinInspection())
    }

    @Test
    fun highlightsDecorateThenPathlessService() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder()
                .service(grpcService.decorate(LoggingService.newDecorator()))
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 1)
    }

    @Test
    fun allowsServiceWithDecoratorExtraArgs() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder()
                .service(grpcService, LoggingService.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 0)
    }

    @Test
    fun allowsDecorateWhenServiceHasExplicitPath() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder()
                .service("/grpc", grpcService.decorate(LoggingService.newDecorator()))
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 0)
    }

    @Test
    fun highlightsGrpcServiceWithoutCors() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder().service(grpcService).build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 1)
    }

    @Test
    fun allowsGrpcServiceWithCorsExtraArgs() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder()
                .service(grpcService, CorsService.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 0)
    }

    @Test
    fun allowsGrpcServiceWithBuilderCorsDecorator() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder()
                .decorator(CorsService.newDecorator())
                .service(grpcService)
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun highlightsAuthAfterLoggingOnBuilder() {
        configureServer(
            """
            Server.builder()
                .decorator(LoggingService.newDecorator())
                .decorator(AuthService.newDecorator())
                .service("/api", null as HttpService?)
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 1)
    }

    @Test
    fun allowsLoggingAfterAuthOnBuilder() {
        configureServer(
            """
            Server.builder()
                .decorator(AuthService.newDecorator())
                .decorator(LoggingService.newDecorator())
                .service("/api", null as HttpService?)
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 0)
    }

    @Test
    fun highlightsAuthAfterLoggingOnServiceExtraArgs() {
        configureServer(
            """
            val grpcService = GrpcService.builder().build()
            Server.builder()
                .service(grpcService, LoggingService.newDecorator(), AuthService.newDecorator())
                .build()
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 1)
    }

    private fun configureServer(body: String) {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.HttpService
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.auth.AuthService
            import com.linecorp.armeria.server.cors.CorsService
            import com.linecorp.armeria.server.grpc.GrpcService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                $body
            }
            """.trimIndent(),
        )
    }

    private fun assertHighlights(
        expected: String,
        count: Int,
    ) {
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
