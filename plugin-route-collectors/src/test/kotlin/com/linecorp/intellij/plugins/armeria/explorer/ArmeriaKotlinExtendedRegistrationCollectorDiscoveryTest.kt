package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaKotlinExtendedRegistrationCollectorDiscoveryTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
        registerDiscoveryRegistrationStubs()
    }

    fun testCollectZooKeeperRegistrationViaOf() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener

            fun main() {
                Server.builder()
                    .serverListener(
                        ZooKeeperUpdatingListener.of(
                            "zk://zk.example.com:2181",
                            "/armeria/services",
                            ZooKeeperRegistrationSpec.curator("my-service")))
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("ZooKeeper", discovery.protocol)
        assertEquals("my-service", discovery.path)
        assertEquals("zk://zk.example.com:2181", discovery.target)
        assertNoHttpRouteFor(discovery)
    }

    fun testCollectZooKeeperRegistrationViaBuilder() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener

            fun main() {
                Server.builder()
                    .serverListener(
                        ZooKeeperUpdatingListener
                            .builder("zk://zk.example.com:2181", "/armeria/services", ZooKeeperRegistrationSpec.curator("builder-service"))
                            .sessionTimeoutMillis(10000)
                            .build())
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("ZooKeeper", discovery.protocol)
        assertEquals("builder-service", discovery.path)
    }

    fun testCollectZooKeeperRegistrationViaBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener

            fun main() {
                val listenerBuilder =
                    ZooKeeperUpdatingListener.builder(
                        "zk://zk.example.com:2181",
                        "/armeria/services",
                        ZooKeeperRegistrationSpec.curator("via-builder-var"))
                Server.builder()
                    .serverListener(listenerBuilder.sessionTimeoutMillis(5000).build())
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("via-builder-var", discovery.path)
        assertEquals("zk://zk.example.com:2181", discovery.target)
    }

    fun testCollectRegistrationViaQualifiedProperty() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerListener
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener

            object Holder {
                val listener: ServerListener =
                    EurekaUpdatingListener
                        .builder("https://eureka.example.com/eureka/v2")
                        .appName("held-app")
                        .build()
            }

            fun main() {
                Server.builder()
                    .serverListener(Holder.listener)
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Eureka", discovery.protocol)
        assertEquals("held-app", discovery.path)
        assertEquals("https://eureka.example.com/eureka/v2", discovery.target)
    }

    fun testCollectEurekaRegistrationViaBuilderAppName() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener

            fun main() {
                Server.builder()
                    .serverListener(
                        EurekaUpdatingListener
                            .builder("https://eureka.example.com/eureka/v2")
                            .appName("my-app")
                            .instanceId("i-0001")
                            .build())
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Eureka", discovery.protocol)
        assertEquals("my-app", discovery.path)
        assertEquals("https://eureka.example.com/eureka/v2", discovery.target)
        assertNoHttpRouteFor(discovery)
    }

    fun testCollectEurekaRegistrationViaEndpointGroupIsUnresolved() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.client.EndpointGroup
            import com.linecorp.armeria.common.SessionProtocol
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener

            fun main() {
                Server.builder()
                    .serverListener(
                        EurekaUpdatingListener.of(
                            SessionProtocol.HTTPS,
                            EndpointGroup.of("eureka-1.example.com")))
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Eureka", discovery.protocol)
        assertEquals("(unnamed service)", discovery.path)
        assertTrue(discovery.targetUnresolved)
    }

    fun testCollectConsulRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.consul.ConsulUpdatingListener
            import java.net.URI

            fun main() {
                Server.builder()
                    .serverListener(
                        ConsulUpdatingListener
                            .builder(URI.create("http://consul.example.com:8500"), "consul-svc")
                            .build())
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Consul", discovery.protocol)
        assertEquals("consul-svc", discovery.path)
        assertEquals("http://consul.example.com:8500", discovery.target)
        assertNoHttpRouteFor(discovery)
    }

    fun testCollectNacosRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.nacos.NacosUpdatingListener
            import java.net.URI

            fun main() {
                Server.builder()
                    .serverListener(
                        NacosUpdatingListener
                            .builder(URI.create("http://nacos.example.com:8848/nacos"), "nacos-svc")
                            .groupName("my-group")
                            .build())
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Nacos", discovery.protocol)
        assertEquals("nacos-svc", discovery.path)
        assertEquals("http://nacos.example.com:8848/nacos", discovery.target)
        assertNoHttpRouteFor(discovery)
    }

    fun testCollectRegistrationViaListenerVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener

            fun main() {
                val listener =
                    EurekaUpdatingListener
                        .builder("https://eureka.example.com/eureka/v2")
                        .appName("var-app")
                        .build()
                Server.builder()
                    .serverListener(listener)
                    .build()
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Eureka", discovery.protocol)
        assertEquals("var-app", discovery.path)
    }

    fun testIgnoreNonArmeriaServerListenerCall() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun main() {
                OtherBuilder.builder()
                    .serverListener(Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.DISCOVERY })
    }

    fun testIgnoreNonRegistryUpdatingListener() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            class ZooKeeperUpdatingListener {
                companion object {
                    fun builder(connectionString: String): ZooKeeperUpdatingListener = ZooKeeperUpdatingListener()
                }
            }

            fun main() {
                Server.builder()
                    .serverListener(ZooKeeperUpdatingListener.builder("zk://zk.example.com:2181"))
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.DISCOVERY })
    }

    private fun collectDiscovery(): ArmeriaRoute? =
        ArmeriaRouteCollector.collect(project).firstOrNull { it.routeMatch == RouteMatch.DISCOVERY }

    private fun assertNoHttpRouteFor(discovery: ArmeriaRoute) {
        assertEquals("", discovery.httpMethod)
        assertTrue(discovery.excludeFromDuplicateIndex)
    }
}
