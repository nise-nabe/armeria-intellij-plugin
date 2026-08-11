package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaGrpcRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaProtoRouteDiscoverySupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaGrpcRouteCollectorGateTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        // Classpath gate test does not need Armeria stubs.
    }

    fun testProtoRoutesSkippedWithoutGrpcOnClasspath() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        assertFalse(ArmeriaGrpcRouteCollector.isGrpcOnClasspath(project, GlobalSearchScope.projectScope(project)))
        assertTrue(
            ArmeriaRouteCollector
                .collect(
                    project,
                    includeProtoRoutes = true,
                    contributors = listOf(ArmeriaProtocolRouteContributor),
                ).none { it.path == "/com.example.Greeter/SayHello" },
        )
    }

    fun testProtoRoutesAppearWhenGrpcClasspathBecomesAvailable() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val contributors = listOf(ArmeriaProtocolRouteContributor)
        val withoutGrpc =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = contributors,
            )
        assertTrue(withoutGrpc.none { it.path == "/com.example.Greeter/SayHello" })

        ArmeriaRouteCollector.collect(
            project,
            includeProtoRoutes = true,
            contributors = contributors,
        )
        assertEquals(0, ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned)

        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )

        val withGrpc =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = contributors,
            )
        assertTrue(withGrpc.any { it.path == "/com.example.Greeter/SayHello" })
        assertTrue(ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned > 0)
    }

    fun testProtoRouteMergeCacheInvalidatesOnProtoEdit() {
        registerArmeriaServerStubs()
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val contributors = listOf(ArmeriaProtocolRouteContributor)
        val first =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = contributors,
            )
        assertEquals(listOf("/com.example.Greeter/SayHello"), first.map { it.path })
        assertTrue(ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned > 0)

        val cached =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = contributors,
            )
        assertEquals(first.map { it.path }, cached.map { it.path })
        assertEquals(0, ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned)

        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
              rpc SayGoodbye(GoodbyeRequest) returns (GoodbyeResponse);
            }
            """.trimIndent(),
        )

        val afterEdit =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = contributors,
            )
        assertEquals(
            listOf("/com.example.Greeter/SayGoodbye", "/com.example.Greeter/SayHello"),
            afterEdit.map { it.path },
        )
        assertTrue(ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned > 0)
    }

    fun testWarmProtoOverlayCacheInvalidatesOnProjectRootChange() {
        registerArmeriaServerStubs()
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val contributors = listOf(ArmeriaProtocolRouteContributor)
        val first =
            ArmeriaRouteCollector.collect(
                project,
                includeProtoRoutes = true,
                contributors = contributors,
            )
        assertEquals(listOf("/com.example.Greeter/SayHello"), first.map { it.path })
        assertTrue(ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned > 0)

        ArmeriaRouteCollector.collect(
            project,
            includeProtoRoutes = true,
            contributors = contributors,
        )
        assertEquals(0, ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned)

        val extraRoot = myFixture.tempDirFixture.findOrCreateDir("extra-root")
        try {
            PsiTestUtil.addSourceRoot(module, extraRoot, false)
            val afterRootChange =
                ArmeriaRouteCollector.collect(
                    project,
                    includeProtoRoutes = true,
                    contributors = contributors,
                )
            assertEquals(first.map { it.path }, afterRootChange.map { it.path })
            assertTrue(ArmeriaRouteCollectionMetrics.lastSnapshot!!.filesScanned > 0)
        } finally {
            PsiTestUtil.removeSourceRoot(module, extraRoot)
        }
    }

    fun testIsGrpcOnClasspathMemoizedForProjectScope() {
        val scope = GlobalSearchScope.projectScope(project)
        assertFalse(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))
        assertFalse(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))

        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )

        assertTrue(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))
        assertTrue(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))
    }

    fun testIsGrpcOnClasspathInvalidatesOnProjectRootChange() {
        val scope = GlobalSearchScope.projectScope(project)
        val grpcClass =
            myFixture.addClass(
                """
                package com.linecorp.armeria.server.grpc;

                public final class GrpcService {
                    public static GrpcServiceBuilder builder(Object bindableService) {
                        return null;
                    }
                }
                """.trimIndent(),
            )
        assertTrue(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))
        assertTrue(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))

        val extraRoot = myFixture.tempDirFixture.findOrCreateDir("grpc-extra-root")
        try {
            PsiTestUtil.addSourceRoot(module, extraRoot, false)
            ApplicationManager.getApplication().runWriteAction {
                grpcClass.containingFile.virtualFile.delete(this)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            assertFalse(ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, scope))
        } finally {
            PsiTestUtil.removeSourceRoot(module, extraRoot)
        }
    }
}
