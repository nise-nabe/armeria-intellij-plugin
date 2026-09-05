package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaServerDecoratorInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        myFixture.registerServerDecoratorInspectionStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaServerDecoratorInspection())
    }

    @Test
    fun highlightsDecorateThenPathlessService() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .service(grpcService.decorate(LoggingService.newDecorator()))
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 1)
    }

    @Test
    fun highlightsDecorateThenPathlessSamlService() {
        configureServer(
            """
            SamlServiceProvider ssp = SamlServiceProvider.builder().build();
            Server.builder()
                  .service(ssp.newSamlService().decorate(LoggingService.newDecorator()))
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 1)
    }

    @Test
    fun allowsServiceWithDecoratorExtraArgs() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .service(grpcService, LoggingService.newDecorator())
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 0)
    }

    @Test
    fun allowsDecorateWhenServiceHasExplicitPath() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .service("/grpc", grpcService.decorate(LoggingService.newDecorator()))
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 0)
    }

    @Test
    fun highlightsDecorateThenPathlessFileService() {
        configureServer(
            """
            FileService files = FileService.ofHttp("/var/www");
            Server.builder()
                  .service(files.decorate(LoggingService.newDecorator()))
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 1)
    }

    @Test
    fun allowsGrpcServiceWithClassLiteralCorsDecorator() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .decorator(CorsService.class)
                  .service(grpcService)
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun allowsGrpcServiceWithCorsDecoratorOutsideNestedBlock() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            ServerBuilder sb = Server.builder();
            sb.decorator(CorsService.newDecorator());
            if (true) {
                sb.service(grpcService).build();
            }
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun highlightsDecorateThenPathlessServiceOnSplitVariable() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            HttpService decorated = grpcService.decorate(LoggingService.newDecorator());
            Server.builder().service(decorated).build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 1)
    }

    @Test
    fun highlightsGrpcServiceWithoutCors() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder().service(grpcService).build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 1)
    }

    @Test
    fun allowsGrpcServiceWithCorsExtraArgs() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .service(grpcService, CorsService.newDecorator())
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
        assertHighlights(message("inspection.server.decorator.service.with.routes"), 0)
    }

    @Test
    fun allowsGrpcServiceWithBuilderCorsDecorator() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .decorator(CorsService.newDecorator())
                  .service(grpcService)
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun allowsGrpcServiceWithRootDecoratorUnderCors() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .decoratorUnder("/", CorsService.newDecorator())
                  .service(grpcService)
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun highlightsGrpcWhenDecoratorUnderCorsDoesNotCoverRoot() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .decoratorUnder("/public", CorsService.newDecorator())
                  .service(grpcService)
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 1)
    }

    @Test
    fun allowsGrpcServiceWithStatementStyleCorsDecorator() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            ServerBuilder sb = Server.builder();
            sb.decorator(CorsService.newDecorator());
            sb.service(grpcService).build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun allowsGrpcServiceWithCorsDecoratorAfterService() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .service(grpcService)
                  .decorator(CorsService.newDecorator())
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun allowsGrpcWhenDecoratorUnderMatchesRegistrationPath() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .decoratorUnder("/grpc", CorsService.newDecorator())
                  .service("/grpc", grpcService)
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.grpc.cors"), 0)
    }

    @Test
    fun highlightsAuthAfterLoggingOnSplitStatements() {
        configureServer(
            """
            ServerBuilder sb = Server.builder();
            sb.decorator(LoggingService.newDecorator());
            sb.decorator(AuthService.newDecorator());
            sb.service("/api", (HttpService) null).build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 1)
    }

    @Test
    fun highlightsAuthAfterLoggingOnBuilder() {
        configureServer(
            """
            Server.builder()
                  .decorator(LoggingService.newDecorator())
                  .decorator(AuthService.newDecorator())
                  .service("/api", (HttpService) null)
                  .build();
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
                  .service("/api", (HttpService) null)
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 0)
    }

    @Test
    fun highlightsAuthAfterLoggingOnServiceExtraArgs() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            Server.builder()
                  .service(grpcService, LoggingService.newDecorator(), AuthService.newDecorator())
                  .build();
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 1)
    }

    @Test
    fun highlightsAuthAfterLoggingOnDecorateChain() {
        configureServer(
            """
            GrpcService grpcService = GrpcService.builder().build();
            grpcService.decorate(LoggingService.newDecorator())
                       .decorate(AuthService.newDecorator());
            """.trimIndent(),
        )
        assertHighlights(message("inspection.server.decorator.auth.after.logging"), 1)
    }

    private fun configureServer(body: String) {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.HttpService;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;
            import com.linecorp.armeria.server.auth.AuthService;
            import com.linecorp.armeria.server.cors.CorsService;
            import com.linecorp.armeria.server.file.FileService;
            import com.linecorp.armeria.server.grpc.GrpcService;
            import com.linecorp.armeria.server.logging.LoggingService;
            import com.linecorp.armeria.server.saml.SamlServiceProvider;

            public class Main {
                public static void main(String[] args) {
                    $body
                }
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
