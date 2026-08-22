package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.CoreServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKnownHttpServiceClassifier
import com.linecorp.intellij.plugins.armeria.explorer.support.KnownHttpServiceKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaKnownHttpServiceClassifierTest {
    @Test
    fun classify_knownFqcnAndSimpleNames() {
        assertEquals(
            KnownHttpServiceKind.DOC_SERVICE,
            ArmeriaKnownHttpServiceClassifier.classify("com.linecorp.armeria.server.docs.DocService"),
        )
        assertEquals(
            KnownHttpServiceKind.DOC_SERVICE,
            ArmeriaKnownHttpServiceClassifier.classify("DocServiceBuilder"),
        )
        assertEquals(
            KnownHttpServiceKind.GRPC,
            ArmeriaKnownHttpServiceClassifier.classify("com.linecorp.armeria.server.grpc.GrpcServiceBuilder"),
        )
        assertEquals(
            KnownHttpServiceKind.GRAPHQL,
            ArmeriaKnownHttpServiceClassifier.classify("GraphqlService"),
        )
        assertEquals(
            KnownHttpServiceKind.THRIFT,
            ArmeriaKnownHttpServiceClassifier.classify("THttpService"),
        )
        assertEquals(
            KnownHttpServiceKind.METRICS,
            ArmeriaKnownHttpServiceClassifier.classify(
                "com.linecorp.armeria.server.metric.PrometheusExpositionService",
            ),
        )
        assertEquals(
            KnownHttpServiceKind.FILE,
            ArmeriaKnownHttpServiceClassifier.classify("FileService"),
        )
        assertEquals(
            KnownHttpServiceKind.FILE,
            ArmeriaKnownHttpServiceClassifier.classify("new FileService()"),
        )
        assertEquals(
            KnownHttpServiceKind.WEBSOCKET,
            ArmeriaKnownHttpServiceClassifier.classify(
                "com.linecorp.armeria.server.websocket.WebSocketService",
            ),
        )
        assertEquals(
            KnownHttpServiceKind.WEBSOCKET,
            ArmeriaKnownHttpServiceClassifier.classify("WebSocketServiceBuilder"),
        )
        assertEquals(
            KnownHttpServiceKind.HEALTH_CHECK,
            ArmeriaKnownHttpServiceClassifier.classify(
                "com.linecorp.armeria.server.healthcheck.HealthCheckService",
            ),
        )
        assertEquals(
            KnownHttpServiceKind.SSE,
            ArmeriaKnownHttpServiceClassifier.classify(
                "com.linecorp.armeria.server.streaming.ServerSentEvents",
            ),
        )
        assertEquals(
            KnownHttpServiceKind.DOC_SERVICE,
            ArmeriaKnownHttpServiceClassifier.classify("new DocService()"),
        )
    }

    @Test
    fun classify_doesNotMatchEmbeddedSimpleNames() {
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.HelloGrpcService"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("MyDocServiceHelper"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.FileService"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.FileService#get()"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.FileService.of()"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("new example.FileService()"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.WebSocketService"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.WebSocketService.of()"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.HealthCheckService"),
        )
        assertEquals(
            KnownHttpServiceKind.HTTP,
            ArmeriaKnownHttpServiceClassifier.classify("example.ServerSentEvents"),
        )
    }

    @Test
    fun classify_scansBuilderChainTokens() {
        assertEquals(
            KnownHttpServiceKind.DOC_SERVICE,
            ArmeriaKnownHttpServiceClassifier.classify("DocService.builder().build()"),
        )
        assertEquals(
            KnownHttpServiceKind.GRPC,
            ArmeriaKnownHttpServiceClassifier.classify("GrpcService.builder().addService(svc).build()"),
        )
        assertEquals(
            KnownHttpServiceKind.DOC_SERVICE,
            ArmeriaKnownHttpServiceClassifier.classify(
                "com.linecorp.armeria.server.docs.DocService.builder().build()",
            ),
        )
        assertEquals(
            KnownHttpServiceKind.WEBSOCKET,
            ArmeriaKnownHttpServiceClassifier.classify("WebSocketService.of(handler)"),
        )
        assertEquals(
            KnownHttpServiceKind.SSE,
            ArmeriaKnownHttpServiceClassifier.classify("ServerSentEvents.fromPublisher(publisher)"),
        )
        assertEquals(
            KnownHttpServiceKind.HEALTH_CHECK,
            ArmeriaKnownHttpServiceClassifier.classify("HealthCheckService.builder().build()"),
        )
    }

    @Test
    fun classify_firstNonHttpHintWins() {
        assertEquals(
            KnownHttpServiceKind.GRPC,
            ArmeriaKnownHttpServiceClassifier.classify(
                "example.HelloGrpcService",
                "com.linecorp.armeria.server.grpc.GrpcService",
            ),
        )
    }

    @Test
    fun protocolAndRouteMatch_docServiceAndMetrics() {
        val docs = KnownHttpServiceKind.DOC_SERVICE
        assertEquals(RouteProtocol.DOC_SERVICE, ArmeriaKnownHttpServiceClassifier.protocol(docs))
        assertEquals(
            RouteMatch.NON_HTTP,
            ArmeriaKnownHttpServiceClassifier.routeMatch(docs, CoreServiceRegistrationMethod.SERVICE),
        )
        assertTrue(ArmeriaKnownHttpServiceClassifier.isDocService(docs))

        val metrics = KnownHttpServiceKind.METRICS
        assertEquals(RouteProtocol.HTTP, ArmeriaKnownHttpServiceClassifier.protocol(metrics))
        assertEquals(
            RouteMatch.SERVICE,
            ArmeriaKnownHttpServiceClassifier.routeMatch(metrics, CoreServiceRegistrationMethod.SERVICE),
        )
        assertEquals(
            RouteMatch.SERVICE_UNDER,
            ArmeriaKnownHttpServiceClassifier.routeMatch(metrics, CoreServiceRegistrationMethod.SERVICE_UNDER),
        )
        assertFalse(ArmeriaKnownHttpServiceClassifier.isDocService(metrics))
        assertTrue(ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex(metrics))
    }

    @Test
    fun routeMatch_fileServiceViaServiceRegistration() {
        assertEquals(
            RouteMatch.FILE_SERVICE,
            ArmeriaKnownHttpServiceClassifier.routeMatch(
                KnownHttpServiceKind.FILE,
                CoreServiceRegistrationMethod.SERVICE,
            ),
        )
        assertEquals(
            RouteMatch.ANNOTATED_SERVICE,
            ArmeriaKnownHttpServiceClassifier.routeMatch(
                KnownHttpServiceKind.FILE,
                CoreServiceRegistrationMethod.ANNOTATED_SERVICE,
            ),
        )
    }

    @Test
    fun protocolAndRouteMatch_websocketSseAndHealthCheck() {
        val websocket = KnownHttpServiceKind.WEBSOCKET
        assertEquals(RouteProtocol.WEBSOCKET, ArmeriaKnownHttpServiceClassifier.protocol(websocket))
        assertEquals(
            RouteMatch.SERVICE,
            ArmeriaKnownHttpServiceClassifier.routeMatch(websocket, CoreServiceRegistrationMethod.SERVICE),
        )
        assertEquals(
            RouteMatch.SERVICE_UNDER,
            ArmeriaKnownHttpServiceClassifier.routeMatch(websocket, CoreServiceRegistrationMethod.SERVICE_UNDER),
        )
        assertEquals("", ArmeriaKnownHttpServiceClassifier.defaultHttpMethod(websocket))
        assertFalse(ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex(websocket))

        val sse = KnownHttpServiceKind.SSE
        assertEquals(RouteProtocol.SSE, ArmeriaKnownHttpServiceClassifier.protocol(sse))
        assertEquals(
            RouteMatch.SERVICE,
            ArmeriaKnownHttpServiceClassifier.routeMatch(sse, CoreServiceRegistrationMethod.SERVICE),
        )
        assertEquals("GET", ArmeriaKnownHttpServiceClassifier.defaultHttpMethod(sse))

        val health = KnownHttpServiceKind.HEALTH_CHECK
        assertEquals(RouteProtocol.HEALTH_CHECK, ArmeriaKnownHttpServiceClassifier.protocol(health))
        assertEquals(
            RouteMatch.HEALTH_CHECK,
            ArmeriaKnownHttpServiceClassifier.routeMatch(health, CoreServiceRegistrationMethod.SERVICE),
        )
        assertEquals(
            RouteMatch.ANNOTATED_SERVICE,
            ArmeriaKnownHttpServiceClassifier.routeMatch(
                health,
                CoreServiceRegistrationMethod.ANNOTATED_SERVICE,
            ),
        )
        assertEquals("GET", ArmeriaKnownHttpServiceClassifier.defaultHttpMethod(health))
        assertFalse(ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex(health))
    }

    @Test
    fun excludeFromDuplicateIndex_usesTargetName() {
        assertTrue(
            ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex(
                "com.linecorp.armeria.server.metric.PrometheusExpositionService",
            ),
        )
        assertFalse(ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex("example.HelloService"))
    }
}
