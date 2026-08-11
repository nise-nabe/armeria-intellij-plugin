package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiManager
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Collector-level proto overlay cache behavior. Uses a test [RouteContributor] instead of
 * [com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor]
 * so ownership stays in `plugin-route-collectors`.
 */
class ArmeriaRouteCollectorProtoCacheTest : ArmeriaFixtureTestBase() {
    override fun setUp() {
        super.setUp()
        TestProtoOverlayContributor.overlayInvocations.set(0)
    }

    fun testProtoOverlayIsCachedAcrossCollectCalls() {
        val first =
            collectWithProtoOverlay()
        assertTrue(first.any { it.path == OVERLAY_PATH })
        assertEquals(1, TestProtoOverlayContributor.overlayInvocations.get())

        val second = collectWithProtoOverlay()
        assertTrue(second.any { it.path == OVERLAY_PATH })
        assertEquals(1, TestProtoOverlayContributor.overlayInvocations.get())

        val withoutProto =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = false,
                contributors = listOf(TestProtoOverlayContributor),
            )
        assertTrue(withoutProto.none { it.path == OVERLAY_PATH })
    }

    fun testProtoOverlayCacheInvalidatesOnPsiEdit() {
        collectWithProtoOverlay()
        assertEquals(1, TestProtoOverlayContributor.overlayInvocations.get())

        collectWithProtoOverlay()
        assertEquals(1, TestProtoOverlayContributor.overlayInvocations.get())

        myFixture.configureByText(
            "Touch.java",
            """
            package example;

            public class Touch {
            }
            """.trimIndent(),
        )

        collectWithProtoOverlay()
        assertEquals(2, TestProtoOverlayContributor.overlayInvocations.get())
    }

    fun testProtoOverlayCacheInvalidatesOnBaseRouteEdit() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val first = collectWithProtoOverlay()
        assertTrue(first.any { it.path == "/hello" })
        assertTrue(first.any { it.path == OVERLAY_PATH })
        assertEquals(1, TestProtoOverlayContributor.overlayInvocations.get())

        collectWithProtoOverlay()
        assertEquals(1, TestProtoOverlayContributor.overlayInvocations.get())

        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello-updated")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val afterEdit = collectWithProtoOverlay()
        assertTrue(afterEdit.any { it.path == "/hello-updated" })
        assertTrue(afterEdit.none { it.path == "/hello" })
        assertTrue(afterEdit.any { it.path == OVERLAY_PATH })
        assertEquals(2, TestProtoOverlayContributor.overlayInvocations.get())
    }

    private fun collectWithProtoOverlay() =
        ArmeriaRouteCollector.collect(
            project,
            includeProtoRoutes = true,
            contributors = listOf(TestProtoOverlayContributor),
        )

    private object TestProtoOverlayContributor : RouteContributor {
        val overlayInvocations = AtomicInteger(0)

        override fun collect(context: RouteCollectContext) {}

        override fun collectProtoOverlay(context: RouteCollectContext) {
            overlayInvocations.incrementAndGet()
            val anchor =
                context.routes
                    .firstOrNull()
                    ?.pointer
                    ?.element
                    ?: context.project.projectFile
                        ?.let { PsiManager.getInstance(context.project).findFile(it) }
                    ?: return
            context.routes +=
                com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute.create(
                    element = anchor,
                    protocol = "test-proto",
                    httpMethod = "",
                    path = OVERLAY_PATH,
                    target = "TestProtoOverlay",
                    routeMatch = RouteMatch.NON_HTTP,
                )
        }
    }

    private companion object {
        const val OVERLAY_PATH = "/test-proto-overlay"
    }
}
