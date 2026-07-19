package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerServletServiceStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server.tomcat;

        public final class TomcatService {
            public static TomcatService of(Object connector) {
                return null;
            }
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.jetty;

        public final class JettyService {
            public static JettyService of(Object server) {
                return null;
            }
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerSpringWebMvcStubs() {
    this.addClass(
        """
        package org.springframework.stereotype;

        public @interface Controller {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.web.bind.annotation;

        public @interface RestController {
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.web.bind.annotation;

        public enum RequestMethod {
            GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.web.bind.annotation;

        public @interface RequestMappings {
            RequestMapping[] value();
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.web.bind.annotation;

        @java.lang.annotation.Repeatable(RequestMappings.class)
        public @interface RequestMapping {
            String[] value() default {};
            String[] path() default {};
            RequestMethod[] method() default {};
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.web.bind.annotation;

        public @interface GetMapping {
            String[] value() default {};
            String[] path() default {};
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package org.springframework.web.bind.annotation;

        public @interface PostMapping {
            String[] value() default {};
            String[] path() default {};
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.configureTomcatMount(path: String) {
    this.configureByText(
        "ArmeriaConfig.java",
        """
        package example;

        import com.linecorp.armeria.server.Server;
        import com.linecorp.armeria.server.tomcat.TomcatService;

        public class ArmeriaConfig {
            public static void main(String[] args) {
                Server.builder()
                    .serviceUnder("$path", TomcatService.of(null))
                    .build();
            }
        }
        """.trimIndent(),
    )
}
