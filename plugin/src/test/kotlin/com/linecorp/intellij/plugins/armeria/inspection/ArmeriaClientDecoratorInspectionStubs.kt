package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerClientDecoratorInspectionStubs() {
    addClass(
        """
        package com.linecorp.armeria.client;

        public final class WebClient {
            public static WebClientBuilder builder(String uri) {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public final class WebClientBuilder {
            public WebClientBuilder decorator(Object decorator) {
                return this;
            }

            public com.linecorp.armeria.client.WebClient build() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.logging;

        public final class LoggingClient {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.retry;

        public final class RetryingClient {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.circuitbreaker;

        public final class CircuitBreakerClient {
            public static Object newDecorator() {
                return null;
            }
        }
        """.trimIndent(),
    )
}
