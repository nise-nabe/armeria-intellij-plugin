package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerResolvableArmeriaServerStubs() {

        this.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return new ServerBuilder();
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server;

            public final class ServerBuilder {
                public ServerBuilder service(String path, Object service) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

fun JavaCodeInsightTestFixture.registerArmeriaServerStubs() {

        this.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server;

            public final class ServerBuilder {
                public ServerBuilder service(String path, Object service) {
                    return this;
                }

                public ServerBuilder serviceUnder(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder decorator(Object decorator) {
                    return this;
                }

                public ServerBuilder decorator(String pathPattern, Object decorator) {
                    return this;
                }

                public ServerBuilder requestTimeout() {
                    return this;
                }

                public ServerBuilder requestTimeout(Object duration) {
                    return this;
                }

                public ServerBuilder responseTimeout(Object duration) {
                    return this;
                }

                public ServerBuilder idleTimeout(Object duration) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }

fun JavaCodeInsightTestFixture.registerMinimalArmeriaServerStubs() {

        this.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        this.addClass(
            """
            package com.linecorp.armeria.server;

            public final class ServerBuilder {
                public ServerBuilder service(String path, Object service) {
                    return this;
                }

                public ServerBuilder serviceUnder(String prefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
