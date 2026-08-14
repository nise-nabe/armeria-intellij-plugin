package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaSpringBootConfiguratorBeanCollectorTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaServerStubs()
        registerSpringAnnotationStubs()
        registerArmeriaSpringStubs()
    }

    @Test
    fun collectsBeanMethodsForEachConfiguratorType() {
        myFixture.configureByText(
            "ArmeriaConfiguration.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import com.linecorp.armeria.spring.DocServiceConfigurator;
            import com.linecorp.armeria.spring.HealthCheckServiceConfigurator;
            import com.linecorp.armeria.spring.MetricCollectingServiceConfigurator;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class ArmeriaConfiguration {
                @Bean
                public ArmeriaServerConfigurator armeriaServerConfigurator() {
                    return serverBuilder -> {};
                }

                @Bean
                public DocServiceConfigurator docServiceConfigurator() {
                    return builder -> {};
                }

                @Bean
                public HealthCheckServiceConfigurator healthCheckServiceConfigurator() {
                    return builder -> {};
                }

                @Bean
                public MetricCollectingServiceConfigurator metricCollectingServiceConfigurator() {
                    return builder -> {};
                }
            }
            """.trimIndent(),
        )

        val beans =
            ArmeriaSpringBootConfiguratorBeanCollector
                .collect(project)
                .single()
                .entries
        val byFqn = beans.associateBy { it.configuratorFqn }
        assertEquals(4, beans.size, beans.map { it.key }.toString())
        assertNotNull(byFqn[ArmeriaRouteSupport.ARMERIA_SERVER_CONFIGURATOR_CLASS])
        assertNotNull(byFqn[ArmeriaRouteSupport.DOC_SERVICE_CONFIGURATOR_CLASS])
        assertNotNull(byFqn[ArmeriaRouteSupport.HEALTH_CHECK_SERVICE_CONFIGURATOR_CLASS])
        assertNotNull(byFqn[ArmeriaRouteSupport.METRIC_COLLECTING_SERVICE_CONFIGURATOR_CLASS])
        assertTrue(beans.all { it.navigationPointer?.element != null })
    }

    @Test
    fun collectsClassesThatImplementConfigurator() {
        myFixture.configureByText(
            "RoutingConfigurator.java",
            """
            package example;

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
            import com.linecorp.armeria.server.ServerBuilder;

            public final class RoutingConfigurator implements ArmeriaServerConfigurator {
                @Override
                public void configure(ServerBuilder serverBuilder) {
                }
            }
            """.trimIndent(),
        )

        val beans =
            ArmeriaSpringBootConfiguratorBeanCollector
                .collect(project)
                .single()
                .entries
        assertEquals(1, beans.size)
        assertEquals("RoutingConfigurator", beans.single().key)
        assertEquals("ArmeriaServerConfigurator", beans.single().value)
        assertNotNull(beans.single().navigationPointer?.element)
    }

    @Test
    fun collectsKotlinBeanFunction() {
        myFixture.configureByText(
            "ArmeriaConfiguration.kt",
            """
            package example

            import com.linecorp.armeria.spring.ArmeriaServerConfigurator
            import org.springframework.context.annotation.Bean
            import org.springframework.context.annotation.Configuration

            @Configuration
            class ArmeriaConfiguration {
                @Bean
                fun armeriaServerConfigurator(): ArmeriaServerConfigurator =
                    ArmeriaServerConfigurator { _ -> }
            }
            """.trimIndent(),
        )

        val beans =
            ArmeriaSpringBootConfiguratorBeanCollector
                .collect(project)
                .single()
                .entries
        assertEquals(1, beans.size)
        assertEquals("armeriaServerConfigurator", beans.single().key)
        assertEquals("ArmeriaServerConfigurator", beans.single().value)
        assertNotNull(beans.single().navigationPointer?.element)
    }
}
