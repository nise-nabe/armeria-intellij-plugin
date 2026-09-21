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

    fun testCollectZooKeeperRegistrationViaBuilderVariable() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListenerBuilder;

            public class Main {
                public static void main(String[] args) {
                    ZooKeeperUpdatingListenerBuilder listenerBuilder =
                        ZooKeeperUpdatingListener.builder(
                            "zk://zk.example.com:2181",
                            "/armeria/services",
                            ZooKeeperRegistrationSpec.curator("via-builder-var"));
                    Server.builder()
                        .serverListener(listenerBuilder.sessionTimeoutMillis(5000).build())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("via-builder-var", discovery.path)
        assertEquals("zk://zk.example.com:2181", discovery.target)
    }

    fun testCollectZooKeeperRegistrationViaListenerVariable() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerListener;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    ServerListener listener =
                        ZooKeeperUpdatingListener.of(
                            "zk://zk.example.com:2181",
                            "/armeria/services",
                            ZooKeeperRegistrationSpec.curator("via-listener-var"));
                    Server.builder()
                        .serverListener(listener)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("via-listener-var", discovery.path)
        assertEquals("zk://zk.example.com:2181", discovery.target)
    }

    fun testCollectZooKeeperRegistrationViaCuratorClient() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperRegistrationSpec;
            import com.linecorp.armeria.server.zookeeper.ZooKeeperUpdatingListener;
            import org.apache.curator.framework.CuratorFramework;

            public class Main {
                public static void main(String[] args) {
                    CuratorFramework client = null;
                    Server.builder()
                        .serverListener(
                            ZooKeeperUpdatingListener.of(
                                client,
                                "/armeria/services",
                                ZooKeeperRegistrationSpec.curator("curator-svc")))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("curator-svc", discovery.path)
        assertTrue(discovery.targetUnresolved)
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
            import java.net.URI;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(EurekaUpdatingListener.of(URI.create("https://eureka.example.com/eureka/v2")))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val discovery = collectDiscovery()
        kotlinAssertNotNull(discovery)
        assertEquals("Eureka", discovery.protocol)
        assertEquals("(unnamed service)", discovery.path)
        assertEquals("https://eureka.example.com/eureka/v2", discovery.target)
    }

    fun testCollectEurekaRegistrationViaEndpointGroupIsUnresolved() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.EndpointGroup;
            import com.linecorp.armeria.common.SessionProtocol;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(
                            EurekaUpdatingListener.of(
                                SessionProtocol.HTTPS,
                                EndpointGroup.of("eureka-1.example.com")))
                        .build();
                }
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.consul.ConsulUpdatingListener;
            import java.net.URI;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(
                            ConsulUpdatingListener
                                .builder(URI.create("http://consul.example.com:8500"), "consul-svc")
                                .build())
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

    fun testCollectNacosRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.nacos.NacosUpdatingListener;
            import java.net.URI;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(
                            NacosUpdatingListener
                                .builder(URI.create("http://nacos.example.com:8848/nacos"), "nacos-svc")
                                .groupName("my-group")
                                .build())
                        .build();
                }
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    ServerBuilder sb = Server.builder();
                    sb.serverListener(
                            EurekaUpdatingListener
                                .builder("https://eureka.example.com/eureka/v2")
                                .appName("var-app")
                                .build())
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

    fun testIgnoreNonRegistryUpdatingListener() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(ZooKeeperUpdatingListener.builder("zk://zk.example.com:2181"))
                        .build();
                }
            }

            final class ZooKeeperUpdatingListener {
                static ZooKeeperUpdatingListener builder(String connectionString) {
                    return null;
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
