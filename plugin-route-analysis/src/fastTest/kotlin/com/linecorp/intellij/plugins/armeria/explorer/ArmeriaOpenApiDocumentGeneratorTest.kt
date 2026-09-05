package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRouteHint
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaOpenApiDocumentGenerator
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaOpenApiDocumentGeneratorTest {
    @Test
    fun exportable_annotatedHttpGet() {
        assertTrue(ArmeriaOpenApiDocumentGenerator.exportable(route()))
    }

    @Test
    fun exportable_rejectsBlankMethod() {
        assertFalse(ArmeriaOpenApiDocumentGenerator.exportable(route(httpMethod = "")))
    }

    @Test
    fun exportable_rejectsPrefixRegexAndGlob() {
        assertFalse(
            ArmeriaOpenApiDocumentGenerator.exportable(
                route(path = "/hello", pathType = PathType.PREFIX),
            ),
        )
        assertFalse(
            ArmeriaOpenApiDocumentGenerator.exportable(
                route(path = "/users/.*/id", pathType = PathType.REGEX),
            ),
        )
        assertFalse(
            ArmeriaOpenApiDocumentGenerator.exportable(
                route(path = "/users/*/id", pathType = PathType.GLOB),
            ),
        )
    }

    @Test
    fun exportable_rejectsThriftAndFramedGrpc() {
        assertFalse(
            ArmeriaOpenApiDocumentGenerator.exportable(
                route(
                    protocol = RouteProtocol.THRIFT.presentableName(),
                    httpMethod = "",
                    path = "/HelloService",
                    routeMatch = RouteMatch.NON_HTTP,
                ),
            ),
        )
        assertFalse(
            ArmeriaOpenApiDocumentGenerator.exportable(
                route(
                    protocol = RouteProtocol.GRPC.presentableName(),
                    httpMethod = "",
                    path = "/example.Echo/Ping",
                    routeMatch = RouteMatch.NON_HTTP,
                ),
            ),
        )
    }

    @Test
    fun exportable_unframedGrpcMethod() {
        assertTrue(
            ArmeriaOpenApiDocumentGenerator.exportable(
                unframedGrpc(path = "/example.Echo/Ping"),
            ),
        )
    }

    @Test
    fun document_getUsersIdProducesJson() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        path = "/users/{id}",
                        target = "example.UserService#getUser()",
                        contentHints = listOf(message("route.explorer.hint.produces", "application/json")),
                    ),
                ),
            )

        assertTrue(yaml.contains("openapi: \"3.0.3\""), yaml)
        assertTrue(yaml.contains("\"/users/{id}\""), yaml)
        assertTrue(yaml.contains("get:"), yaml)
        assertTrue(yaml.contains("name: \"id\""), yaml)
        assertTrue(yaml.contains("in: \"path\""), yaml)
        assertTrue(yaml.contains("required: true"), yaml)
        assertTrue(yaml.contains("\"application/json\":"), yaml)
        assertTrue(yaml.contains("type: \"object\""), yaml)
        assertTrue(yaml.contains("\"200\":"), yaml)
        assertFalse(yaml.contains("# Omitted"), yaml)
    }

    @Test
    fun document_colonPathVariableBecomesOpenApiParameter() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(route(path = "/users/:id")),
            )

        assertTrue(yaml.contains("\"/users/{id}\""), yaml)
        assertTrue(yaml.contains("name: \"id\""), yaml)
        assertTrue(yaml.contains("in: \"path\""), yaml)
    }

    @Test
    fun document_postConsumesJsonRequestBody() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        httpMethod = "POST",
                        path = "/items",
                        contentHints =
                            listOf(
                                message("route.explorer.hint.consumes", "application/json"),
                                message("route.explorer.hint.produces", "application/json"),
                            ),
                    ),
                ),
            )

        assertTrue(yaml.contains("post:"), yaml)
        assertTrue(yaml.contains("requestBody:"), yaml)
        assertTrue(yaml.contains("required: true"), yaml)
        assertTrue(yaml.contains("\"application/json\":"), yaml)
    }

    @Test
    fun document_statusCodeAndDescription() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        path = "/created",
                        httpMethod = "POST",
                        contentHints =
                            listOf(
                                message("route.explorer.hint.statusCode", "201"),
                                message("route.explorer.hint.description", "Creates an item"),
                            ),
                    ),
                ),
            )

        assertTrue(yaml.contains("\"201\":"), yaml)
        assertTrue(yaml.contains("description: \"HTTP 201\""), yaml)
        assertTrue(yaml.contains("summary: \"Creates an item\""), yaml)
        assertFalse(yaml.contains("requestBody:"), yaml)
    }

    @Test
    fun document_escapesQuotesInDescription() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        contentHints =
                            listOf(message("route.explorer.hint.description", """Says "hello"""")),
                    ),
                ),
            )

        assertTrue(yaml.contains("summary: \"Says \\\"hello\\\"\""), yaml)
    }

    @Test
    fun document_matchesHeaderAndParam() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        path = "/secure",
                        contentHints =
                            listOf(
                                message("route.explorer.hint.matchesHeader", "authorization=bearer"),
                                message("route.explorer.hint.matchesHeader", "authorization=basic"),
                                message("route.explorer.hint.matchesParam", "verbose=true"),
                            ),
                    ),
                ),
            )

        assertTrue(yaml.contains("name: \"authorization\""), yaml)
        assertTrue(yaml.contains("in: \"header\""), yaml)
        assertTrue(yaml.contains("name: \"verbose\""), yaml)
        assertTrue(yaml.contains("in: \"query\""), yaml)
        assertEquals(1, yaml.lines().count { it.contains("name: \"authorization\"") }, yaml)
    }

    @Test
    fun document_omitsThriftAndFramedGrpcWithComment() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(path = "/users/{id}"),
                    route(
                        protocol = RouteProtocol.THRIFT.presentableName(),
                        httpMethod = "",
                        path = "/HelloService",
                        routeMatch = RouteMatch.NON_HTTP,
                    ),
                    route(
                        protocol = RouteProtocol.GRPC.presentableName(),
                        httpMethod = "",
                        path = "/example.Echo/Ping",
                        routeMatch = RouteMatch.NON_HTTP,
                    ),
                ),
            )

        assertTrue(yaml.contains("\"/users/{id}\""), yaml)
        assertFalse(yaml.contains("HelloService"), yaml)
        assertFalse(yaml.contains("/example.Echo/Ping"), yaml)
        assertTrue(yaml.contains(RouteProtocol.THRIFT.presentableName()), yaml)
        assertTrue(yaml.contains(RouteProtocol.GRPC.presentableName()), yaml)
        assertTrue(yaml.contains("Omitted 2"), yaml)
    }

    @Test
    fun document_unframedGrpcJsonPost() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(unframedGrpc(path = "/example.Echo/Ping", target = "example.Echo.Ping")),
            )

        assertTrue(yaml.contains("\"/example.Echo/Ping\""), yaml)
        assertTrue(yaml.contains("post:"), yaml)
        assertTrue(yaml.contains("requestBody:"), yaml)
        assertTrue(yaml.contains("\"application/json\":"), yaml)
        assertFalse(yaml.contains("Omitted"), yaml)
    }

    @Test
    fun document_duplicatePathMethodFirstWins() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        path = "/users/{id}",
                        target = "first.UserService#get()",
                        contentHints = listOf(message("route.explorer.hint.produces", "application/json")),
                    ),
                    route(
                        path = "/users/{id}",
                        target = "second.UserService#get()",
                        contentHints = listOf(message("route.explorer.hint.produces", "text/plain")),
                    ),
                ),
            )

        assertEquals(1, yaml.lines().count { it.trim() == "get:" }, yaml)
        assertTrue(yaml.contains("application/json"), yaml)
        assertFalse(yaml.contains("text/plain"), yaml)
        assertTrue(yaml.contains("Skipped duplicate GET /users/{id}"), yaml)
    }

    @Test
    fun document_sseAnnotatedHttpIncluded() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        protocol = RouteProtocol.SSE.presentableName(),
                        path = "/events",
                        contentHints = listOf(message("route.explorer.hint.produces", "text/event-stream")),
                    ),
                ),
            )

        assertTrue(yaml.contains("\"/events\""), yaml)
        assertTrue(yaml.contains("\"text/event-stream\":"), yaml)
        assertTrue(yaml.contains("type: \"string\""), yaml)
    }

    @Test
    fun document_emptyExportableStillValidOpenApi() {
        val yaml =
            ArmeriaOpenApiDocumentGenerator.document(
                listOf(
                    route(
                        protocol = RouteProtocol.THRIFT.presentableName(),
                        httpMethod = "",
                        path = "/HelloService",
                        routeMatch = RouteMatch.NON_HTTP,
                    ),
                ),
            )

        assertTrue(yaml.contains("openapi: \"3.0.3\""), yaml)
        assertTrue(yaml.contains("paths: {}"), yaml)
        assertTrue(yaml.contains("Omitted 1"), yaml)
    }

    private fun unframedGrpc(
        path: String,
        target: String = "Handler",
    ): ArmeriaRoute =
        route(
            protocol = RouteProtocol.GRPC.presentableName(),
            httpMethod = "",
            path = path,
            target = target,
            routeMatch = RouteMatch.NON_HTTP,
            contentHints = listOf(GrpcRouteHint.UNFRAMED),
        )

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        protocol: String = RouteProtocol.HTTP.presentableName(),
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        pathType: PathType = PathType.EXACT,
        target: String = "Handler",
        contentHints: List<String> = emptyList(),
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
            target = target,
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = false,
            pathType = pathType,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            contentHints = contentHints,
            pointer = TestPsiPointer,
        )

    private object TestPsiPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
