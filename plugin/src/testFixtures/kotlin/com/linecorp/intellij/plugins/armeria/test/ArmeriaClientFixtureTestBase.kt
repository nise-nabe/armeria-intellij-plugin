package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class ArmeriaClientFixtureTestBase : LightJavaCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        registerArmeriaClientStubs()
    }

    protected fun registerArmeriaClientStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClient {
                public static WebClient of(String uri) {
                    return null;
                }

                public static WebClientBuilder builder() {
                    return null;
                }

                public static WebClientBuilder builder(String uri) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClientBuilder {
                public WebClientBuilder decorator(Object decorator) {
                    return this;
                }

                public WebClientBuilder endpointGroup(Object endpointGroup) {
                    return this;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClient {
                public static GrpcClientBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClientBuilder {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.grpc;

            public final class GrpcClients {
                public static GrpcClientBuilder builder(String uri) {
                    return null;
                }

                public static Object newClient(String uri, Class<?> stubClass) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.thrift;

            public final class ThriftClient {
                public static ThriftClientBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.thrift;

            public final class ThriftClientBuilder {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.thrift;

            public final class ThriftClients {
                public static ThriftClientBuilder builder(String uri) {
                    return null;
                }

                public static Object newClient(String uri, Class<?> ifaceClass) {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
