package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertTrue

/**
 * Classpath gate: without the optional Armeria integration classes
 * (`armeria-zookeeper` / `armeria-eureka` / `armeria-consul`), `serverListener(...)`
 * calls must not produce discovery routes even when the source text names them.
 */
class ArmeriaExtendedRegistrationCollectorDiscoveryClasspathGateTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testNoDiscoveryRouteWhenZooKeeperIntegrationAbsent() {
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

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.DISCOVERY })
    }

    fun testNoDiscoveryRouteWhenEurekaIntegrationAbsent() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .serverListener(EurekaUpdatingListener.of("https://eureka.example.com/eureka/v2", "my-app"))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.DISCOVERY })
    }

    fun testNoDiscoveryRouteWhenConsulIntegrationAbsent() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.consul.ConsulUpdatingListener

            fun main() {
                Server.builder()
                    .serverListener(ConsulUpdatingListener.of("http://consul.example.com:8500", "svc"))
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.DISCOVERY })
    }
}
