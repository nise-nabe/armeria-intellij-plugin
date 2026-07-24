package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

internal fun JavaCodeInsightTestFixture.registerArmeriaJUnitTestSupportStubs() {
    registerArmeriaAnnotationStubs()
    registerArmeriaBlockingAnnotationStubs()
    addClass(
        """
        package org.junit.jupiter.api.extension;

        public @interface RegisterExtension {}
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.testing.junit5.server;

        import com.linecorp.armeria.client.WebClient;
        import com.linecorp.armeria.client.blocking.BlockingWebClient;

        public abstract class ServerExtension {
            public String httpUri() {
                return "";
            }

            public WebClient webClient() {
                return null;
            }

            public BlockingWebClient blockingWebClient() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public class WebClient {
            public static WebClient of(String uri) {
                return null;
            }

            public HttpRequestBuilder get(String path) {
                return null;
            }

            public HttpRequestBuilder post(String path) {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client;

        public class HttpRequestBuilder {
            public Object aggregate() {
                return null;
            }
        }
        """.trimIndent(),
    )
    addClass(
        """
        package com.linecorp.armeria.client.blocking;

        public class BlockingWebClient {
            public Object get(String path) {
                return null;
            }
        }
        """.trimIndent(),
    )
}
