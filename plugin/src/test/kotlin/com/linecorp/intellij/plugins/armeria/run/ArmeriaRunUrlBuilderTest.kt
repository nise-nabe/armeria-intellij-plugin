package com.linecorp.intellij.plugins.armeria.run

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmeriaRunUrlBuilderTest {
    @Test
    fun serviceUrl_docServiceUsesLoopbackAndTrailingSlash() {
        val listen = ArmeriaListenEndpoint(8080)

        assertEquals(
            "http://127.0.0.1:8080/docs/",
            ArmeriaRunUrlBuilder.serviceUrl(listen, "/docs", trailingSlash = true),
        )
    }

    @Test
    fun fromRoutes_combinesProgrammaticPortWithDocServicePath() {
        val urls =
            ArmeriaRunUrlBuilder.fromRoutes(
                ArmeriaListenEndpoint(8080),
                listOf(docServiceRoute("/docs")),
            )

        assertEquals("http://127.0.0.1:8080/docs/", urls.docService)
        assertEquals(8080, urls.listen?.port)
    }

    @Test
    fun fromRoutes_withoutPortSkipsUrls() {
        val urls = ArmeriaRunUrlBuilder.fromRoutes(null, listOf(docServiceRoute("/docs")))

        assertTrue(urls.isEmpty)
        assertNull(urls.listen)
        assertNull(urls.docService)
    }

    @Test
    fun fromRoutes_includesHealthAndMetrics() {
        val urls =
            ArmeriaRunUrlBuilder.fromRoutes(
                ArmeriaListenEndpoint(8080),
                listOf(
                    docServiceRoute("/docs"),
                    route(path = "/internal/healthcheck", routeMatch = RouteMatch.HEALTH_CHECK),
                    route(
                        path = "/metrics",
                        target = "com.linecorp.armeria.server.metric.PrometheusExpositionService",
                    ),
                ),
            )

        assertEquals("http://127.0.0.1:8080/internal/healthcheck", urls.health)
        assertEquals("http://127.0.0.1:8080/metrics", urls.metrics)
    }

    @Test
    fun listenPortFromSpringRoutes_usesFirstPortBinding() {
        val listen =
            springListenPort(
                route(path = ":8080", protocol = "HTTP", routeMatch = RouteMatch.LISTEN_PORT),
                route(path = ":8443", protocol = "HTTPS", routeMatch = RouteMatch.LISTEN_PORT),
            )

        assertEquals(ArmeriaListenEndpoint(8080, https = false), listen)
    }

    @Test
    fun isDefaultApplicationConfigName_prefersUnprofiledFiles() {
        assertTrue(ArmeriaRunUrlBuilder.isDefaultApplicationConfigName("application.properties"))
        assertTrue(ArmeriaRunUrlBuilder.isDefaultApplicationConfigName("application.yml"))
        assertTrue(ArmeriaRunUrlBuilder.isDefaultApplicationConfigName("application.yaml"))
        assertEquals(false, ArmeriaRunUrlBuilder.isDefaultApplicationConfigName("application-prod.yml"))
        assertEquals(false, ArmeriaRunUrlBuilder.isDefaultApplicationConfigName(null))
        assertTrue(ArmeriaRunUrlBuilder.isSpringApplicationConfigName("application.yml"))
        assertTrue(ArmeriaRunUrlBuilder.isSpringApplicationConfigName("application-prod.yml"))
        assertTrue(ArmeriaRunUrlBuilder.isSpringApplicationConfigName("application.yaml"))
        assertTrue(ArmeriaRunUrlBuilder.isSpringApplicationConfigName("application.properties"))
        assertEquals(false, ArmeriaRunUrlBuilder.isSpringApplicationConfigName("Main.java"))
        assertEquals(false, ArmeriaRunUrlBuilder.isSpringApplicationConfigName("ServerConfig.kt"))
        assertEquals(false, ArmeriaRunUrlBuilder.isSpringApplicationConfigName(null))
    }

    @Test
    fun listenPortFromSpringRoutes_httpsOnly() {
        val listen =
            springListenPort(route(path = ":8443", protocol = "HTTPS", routeMatch = RouteMatch.LISTEN_PORT))

        assertEquals(ArmeriaListenEndpoint(8443, https = true), listen)
    }

    @Test
    fun listenPortFromSpringRoutes_h1IsTls() {
        val listen =
            springListenPort(route(path = ":8443", protocol = "H1", routeMatch = RouteMatch.LISTEN_PORT))

        assertEquals(ArmeriaListenEndpoint(8443, https = true), listen)
    }

    @Test
    fun listenPortFromSpringRoutes_h1cIsCleartext() {
        val listen =
            springListenPort(route(path = ":8080", protocol = "H1C", routeMatch = RouteMatch.LISTEN_PORT))

        assertEquals(ArmeriaListenEndpoint(8080, https = false), listen)
    }

    @Test
    fun listenPortFromSpringRoutes_ignoresProgrammaticListenPorts() {
        val programmatic = route(path = ":8080", protocol = "HTTP", routeMatch = RouteMatch.LISTEN_PORT)
        val spring = route(path = ":9090", protocol = "HTTP", routeMatch = RouteMatch.LISTEN_PORT)

        assertNull(
            ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(listOf(programmatic)) { "Main.java" },
        )
        assertEquals(
            ArmeriaListenEndpoint(9090, https = false),
            ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(listOf(programmatic, spring)) { route ->
                if (route.path == ":9090") "application.yml" else "ServerConfig.java"
            },
        )
    }

    @Test
    fun listenPortFromSpringRoutes_prefersDefaultApplicationFileOverProfile() {
        val profiled = route(path = ":9090", protocol = "HTTP", routeMatch = RouteMatch.LISTEN_PORT)
        val default = route(path = ":8080", protocol = "HTTP", routeMatch = RouteMatch.LISTEN_PORT)

        assertEquals(
            ArmeriaListenEndpoint(8080, https = false),
            ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(listOf(profiled, default)) { route ->
                if (route.path == ":8080") "application.yml" else "application-prod.yml"
            },
        )
    }

    @Test
    fun extraArgsSuggestHttps_classifiesArmeriaSessionProtocols() {
        assertTrue(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.H1"))
        assertTrue(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.H2"))
        assertTrue(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.HTTPS"))
        assertFalse(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.H1C"))
        assertFalse(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.H2C"))
        assertFalse(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.HTTP"))
        assertFalse(ArmeriaSessionProtocols.extraArgsSuggestHttps("SessionProtocol.HTTP, SessionProtocol.H1"))
        assertFalse(ArmeriaSessionProtocols.isHttpsOnly("HTTP+HTTPS"))
        assertTrue(ArmeriaSessionProtocols.isHttpsOnly("HTTPS"))
        assertTrue(ArmeriaSessionProtocols.isHttpsOnly("H1"))
    }

    @Test
    fun parsePort_readsPlaceholderDefault() {
        assertEquals(8080, ArmeriaRunUrlBuilder.parsePort("\${SERVER_PORT:8080}"))
        assertEquals(9090, ArmeriaRunUrlBuilder.parsePort("9090"))
        assertNull(ArmeriaRunUrlBuilder.parsePort("0"))
        assertNull(ArmeriaRunUrlBuilder.parsePort("\${SERVER_PORT}"))
    }

    @Test
    fun fromRoutes_usesInternalServicePortForDocs() {
        val urls =
            ArmeriaRunUrlBuilder.fromRoutes(
                ArmeriaListenEndpoint(8080),
                listOf(route(path = "/docs", target = "DocService · :18080", isDocService = true)),
            )

        assertEquals("http://127.0.0.1:18080/docs/", urls.docService)
        assertEquals(8080, urls.listen?.port)
    }

    @Test
    fun httpsBaseUrl() {
        assertEquals(
            "https://127.0.0.1:8443/docs/",
            ArmeriaRunUrlBuilder.serviceUrl(ArmeriaListenEndpoint(8443, https = true), "/docs", trailingSlash = true),
        )
    }

    private fun springListenPort(vararg routes: ArmeriaRoute) =
        ArmeriaRunUrlBuilder.listenPortFromSpringRoutes(routes.toList()) { "application.yml" }

    private fun docServiceRoute(path: String): ArmeriaRoute = route(path = path, isDocService = true)

    private fun route(
        path: String,
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.SERVICE,
        target: String = "Handler",
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
