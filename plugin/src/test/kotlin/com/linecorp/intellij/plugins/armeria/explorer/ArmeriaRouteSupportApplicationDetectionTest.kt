package com.linecorp.intellij.plugins.armeria.explorer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaRouteSupportApplicationDetectionTest {
    @Test
    fun referencesArmeriaApplicationInSource_matchesFqcnServerBuilderCallWithoutImports() {
        val source = """
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
        val source = """
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
        val source = """
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
        val source = """
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
        val source = """
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
    fun referencesArmeriaApplicationInSource_doesNotMatchOtherServerBuilderCalls() {
        val source = """
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
}
