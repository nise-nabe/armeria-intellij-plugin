package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaRouteSupportApplicationDetectionTest {
    @Test
    fun referencesArmeriaApplicationInSource_matchesFqcnServerBuilderCallWithoutImports() {
        val source =
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    com.linecorp.armeria.server.Server.builder().build();
                }
            }
            """.trimIndent()

        assertTrue(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_matchesArmeriaPackageReferenceAfterLongHeader() {
        val longHeader = "/* " + "x".repeat(ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT) + " */"
        val source =
            """
            $longHeader
            package example;

            public class Main {
                public static void main(String[] args) {
                    com.linecorp.armeria.server.Server.builder().build();
                }
            }
            """.trimIndent()

        assertTrue(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_matchesArmeriaImportInHeader() {
        val source =
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().build();
                }
            }
            """.trimIndent()

        assertTrue(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_doesNotMatchUnqualifiedServerBuilderWithoutArmeriaReferences() {
        val source =
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().build();
                }
            }
            """.trimIndent()

        assertFalse(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_doesNotMatchBareServerBuilderIdentifier() {
        val source =
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    serverBuilder.start();
                }
            }
            """.trimIndent()

        assertFalse(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_doesNotMatchFqcnServerBuilderCallInStringLiteral() {
        val source =
            """
            package example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("com.linecorp.armeria.server.Server.builder()");
                }
            }
            """.trimIndent()

        assertFalse(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_doesNotMatchArmeriaClientWithoutServerBuilder() {
        val source =
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.of("https://example.com");
                }
            }
            """.trimIndent()

        assertFalse(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun referencesArmeriaApplicationInSource_doesNotMatchOtherServerBuilderCalls() {
        val source =
            """
            package example;

            import com.example.other.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder().build();
                }
            }
            """.trimIndent()

        assertFalse(ArmeriaRouteSupport.referencesArmeriaApplicationInSource(source))
    }

    @Test
    fun looksLikeRouteDecoratorReceiverText_matchesChainedCall() {
        assertTrue(ArmeriaRouteSupport.looksLikeRouteDecoratorReceiverText("Server.builder().routeDecorator()"))
    }

    @Test
    fun looksLikeRouteDecoratorReceiverText_doesNotMatchUnrelatedIdentifier() {
        assertFalse(ArmeriaRouteSupport.looksLikeRouteDecoratorReceiverText("routeDecoratorFactory"))
    }
}
