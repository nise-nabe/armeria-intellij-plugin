package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceDebugFormUrl
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceExampleApplicator
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceExampleIndex
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceMethodRef
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmeriaDocServiceDebugFormUrlTest {
    @Test
    fun from_annotatedGetUsesClassAndMethod() {
        val route = route(target = "example.HelloService#hello()", routeMatch = RouteMatch.ANNOTATED_HTTP)

        assertEquals(
            ArmeriaDocServiceMethodRef("example.HelloService", "hello"),
            ArmeriaDocServiceMethodRef.from(route),
        )
    }

    @Test
    fun from_grpcUsesProtobufServiceAndMethod() {
        val route =
            route(
                protocol = "gRPC",
                path = "/example.EchoService/Echo",
                target = "example.EchoService.Echo",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertEquals(
            ArmeriaDocServiceMethodRef("example.EchoService", "Echo"),
            ArmeriaDocServiceMethodRef.from(route),
        )
    }

    @Test
    fun from_thriftUsesIdlServiceAndMethod() {
        val route =
            route(
                protocol = "Thrift",
                path = "/HelloService",
                target = "HelloService.hello",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertEquals(
            ArmeriaDocServiceMethodRef("HelloService", "hello"),
            ArmeriaDocServiceMethodRef.from(route),
        )
    }

    @Test
    fun build_encodesMethodPermalink() {
        val url =
            ArmeriaDocServiceDebugFormUrl.build(
                "http://localhost:8080/docs",
                ArmeriaDocServiceMethodRef("example.HelloService", "hello"),
            )

        assertEquals("http://localhost:8080/docs/#/methods/example.HelloService/hello", url)
    }

    @Test
    fun debugFormUrl_usesStaticDocServiceMountWithoutRunningServer() {
        val annotated = route(target = "example.HelloService#hello()", routeMatch = RouteMatch.ANNOTATED_HTTP)
        val docs = route(path = "/docs", protocol = "DocService", isDocService = true)

        assertEquals(
            "http://localhost:8080/docs/#/methods/example.HelloService/hello",
            ArmeriaDocServiceSupport.debugFormUrl(annotated, listOf(annotated, docs)),
        )
    }

    @Test
    fun debugFormUrl_prefersLastSyncedBaseUrl() {
        val annotated = route(target = "example.HelloService#hello()", routeMatch = RouteMatch.ANNOTATED_HTTP)
        val docs = route(path = "/docs", protocol = "DocService", isDocService = true)

        assertEquals(
            "http://127.0.0.1:9090/internal/docs/#/methods/example.HelloService/hello",
            ArmeriaDocServiceSupport.debugFormUrl(
                annotated,
                listOf(annotated, docs),
                lastSyncedBaseUrl = "http://127.0.0.1:9090/internal/docs/",
            ),
        )
    }

    @Test
    fun debugFormUrl_returnsNullWhenRouteHasNoMethodRef() {
        val docs = route(path = "/docs", protocol = "DocService", isDocService = true)
        assertNull(ArmeriaDocServiceSupport.debugFormUrl(docs, listOf(docs)))
    }

    @Test
    fun apply_attachesMatchingExamplesToAnnotatedRoute() {
        val route = route(target = "example.HelloService#hello()", routeMatch = RouteMatch.ANNOTATED_HTTP)
        val examples =
            ArmeriaDocServiceExampleIndex
                .Builder()
                .apply {
                    addRequests("example.HelloService", "hello", listOf("""{"name":"Armeria"}"""))
                    addHeaders("HelloService", null, listOf("authorization: bearer-token"))
                }.build()

        val applied = ArmeriaDocServiceExampleApplicator.apply(listOf(route), examples).single()

        assertEquals(listOf("""{"name":"Armeria"}"""), applied.exampleRequests)
        assertEquals(listOf("authorization: bearer-token"), applied.exampleHeaders)
    }

    @Test
    fun docsBaseUrlFromSpecificationUrl_stripsSpecificationSuffix() {
        assertEquals(
            "http://localhost:8080/docs",
            ArmeriaDocServiceDebugFormUrl.docsBaseUrlFromSpecificationUrl(
                "http://localhost:8080/docs/specification.json",
            ),
        )
    }

    private fun route(
        path: String = "/hello",
        protocol: String = "HTTP",
        target: String = "Handler",
        routeMatch: RouteMatch = RouteMatch.SERVICE,
        isDocService: Boolean = false,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = "GET",
            path = path,
            target = target,
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = isDocService,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
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
