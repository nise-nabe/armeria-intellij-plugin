package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.registerArmeriaAnnotationStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Get {
            String value() default "";
            String path() default "";
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Post {
            String value() default "";
            String path() default "";
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface PathPrefix {
            String value();
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Decorator {
            Class<?>[] value();
        }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface ExceptionHandler {
            Class<?>[] value();
        }
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerArmeriaBlockingAnnotationStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Blocking {}
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface NonBlocking {}
        """.trimIndent(),
    )
}

fun JavaCodeInsightTestFixture.registerContentAnnotationStubs() {
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Param { String value() default ""; }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface StatusCode { int value(); }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Consumes { String[] value(); }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Produces { String[] value(); }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface MatchesHeader { String value(); }
        """.trimIndent(),
    )
    this.addClass(
        """
        package com.linecorp.armeria.server.annotation;

        public @interface Description { String value() default ""; }
        """.trimIndent(),
    )
}
