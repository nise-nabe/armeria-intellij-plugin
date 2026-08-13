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
    fun excludeFromDuplicateIndex_usesTargetName() {
        assertTrue(
            ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex(
                "com.linecorp.armeria.server.metric.PrometheusExpositionService",
            ),
        )
        assertFalse(ArmeriaKnownHttpServiceClassifier.excludeFromDuplicateIndex("example.HelloService"))
    }
}
