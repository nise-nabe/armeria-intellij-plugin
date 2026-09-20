package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaExtendedRegistrationCollectorDiscoveryTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
        registerDiscoveryRegistrationStubs()
    }

    fun testCollectZooKeeperRegistrationViaOf() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(
                            ZooKeeperUpdatingListener.of(
                                "zk://zk.example.com:2181",
                                "/armeria/services",
                                ZooKeeperRegistrationSpec.curator("my-service")))
                        .build();
                }
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(
                            ZooKeeperUpdatingListener
                                .builder(
                                    "zk://zk.example.com:2181",
                                    "/armeria/services",
                                    ZooKeeperRegistrationSpec.curator("builder-service"))
                                .sessionTimeoutMillis(10000)
                                .build())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("ZooKeeper", discovery.protocol)
        assertEquals("builder-service", discovery.path)
        assertEquals("zk://zk.example.com:2181", discovery.target)
    }

    fun testCollectEurekaRegistrationViaBuilderAppName() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(
                            EurekaUpdatingListener
                                .builder("https://eureka.example.com/eureka/v2")
                                .appName("my-app")
                                .instanceId("i-0001")
                                .build())
                        .build();
                }
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

    fun testCollectEurekaRegistrationViaOf() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(EurekaUpdatingListener.of("https://eureka.example.com/eureka/v2", "of-app"))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Eureka", discovery.protocol)
        assertEquals("of-app", discovery.path)
        assertEquals("https://eureka.example.com/eureka/v2", discovery.target)
    }

    fun testCollectConsulRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.consul.ConsulUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(ConsulUpdatingListener.of("http://consul.example.com:8500", "consul-svc"))
                        .build();
                }
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

    fun testCollectRegistrationViaListenerVariable() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    ServerBuilder sb = Server.builder();
                    sb.serverListener(EurekaUpdatingListener.of("https://eureka.example.com/eureka/v2", "var-app"))
                        .build();
                }
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
            "Main.java",
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    OtherBuilder.builder()
                        .serverListener(new Object())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.DISCOVERY })
    }

    fun testIgnoreUnrelatedListenerOnServerBuilder() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(new Object())
                        .build();
                }
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
