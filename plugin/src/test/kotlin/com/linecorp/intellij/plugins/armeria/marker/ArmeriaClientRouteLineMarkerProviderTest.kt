package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.client.ArmeriaKotlinClientCollector
import com.linecorp.intellij.plugins.armeria.test.ArmeriaClientFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.registerArmeriaAnnotationStubs
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaClientRouteLineMarkerProviderTest : ArmeriaClientFixtureTestBase() {
    private val javaProvider = ArmeriaJavaClientRouteLineMarkerProvider()
    private val kotlinProvider = ArmeriaKotlinClientRouteLineMarkerProvider()

    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaAnnotationStubs()
    }

    fun testJavaClientFactoryHasRouteMarkerWhenPathsOverlap() {
        myFixture.addFileToProject(
            "src/Service.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class Service {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Client.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Client {
                public static void main(String[] args) {
                    WebClient.of("https://example.com/hello");
                }
            }
            """.trimIndent(),
        )

        val call = PsiTreeUtil.findChildOfType(myFixture.file, PsiMethodCallExpression::class.java)!!
        val marker = javaProvider.getLineMarkerInfo(call.methodExpression.referenceNameElement!!)

        kotlinAssertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker.icon)
    }

    fun testJavaClientFactoryHasNoMarkerWithoutOverlappingRoute() {
        myFixture.configureByText(
            "Client.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;

            public class Client {
                public static void main(String[] args) {
                    WebClient.of("https://example.com/hello");
                }
            }
            """.trimIndent(),
        )

        val call = PsiTreeUtil.findChildOfType(myFixture.file, PsiMethodCallExpression::class.java)!!
        assertNull(javaProvider.getLineMarkerInfo(call.methodExpression.referenceNameElement!!))
    }

    fun testJavaUnrelatedOfCallHasNoRouteMarker() {
        myFixture.addFileToProject(
            "src/Service.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class Service {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class HttpHeaders {
                public static HttpHeaders of(String name, String value) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Client.java",
            """
            package example;

            import com.linecorp.armeria.common.HttpHeaders;

            public class Client {
                public static void main(String[] args) {
                    HttpHeaders.of("foo", "bar");
                }
            }
            """.trimIndent(),
        )

        val call = PsiTreeUtil.findChildOfType(myFixture.file, PsiMethodCallExpression::class.java)!!
        assertNull(javaProvider.getLineMarkerInfo(call.methodExpression.referenceNameElement!!))
    }

    fun testKotlinClientFactoryHasRouteMarkerWhenPathsOverlap() {
        myFixture.addFileToProject(
            "src/Service.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class Service {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Client.kt",
            """
            package example

            import com.linecorp.armeria.client.RestClient

            fun main() {
                RestClient.of("https://example.com/hello")
            }
            """.trimIndent(),
        )

        val call = PsiTreeUtil.findChildOfType(myFixture.file, KtCallExpression::class.java)!!
        val name = kotlinCallName(call)
        val leaf = name.firstChild ?: name
        val marker = kotlinProvider.getLineMarkerInfo(leaf)

        kotlinAssertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker.icon)
    }

    fun testKotlinUnrelatedOfCallIsNotAnArmeriaClientInvocation() {
        myFixture.addFileToProject(
            "src/Service.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class Service {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.common;

            public final class HttpHeaders {
                public static HttpHeaders of(String name, String value) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Client.kt",
            """
            package example

            import com.linecorp.armeria.common.HttpHeaders

            fun main() {
                HttpHeaders.of("foo", "bar")
            }
            """.trimIndent(),
        )

        val call = PsiTreeUtil.findChildOfType(myFixture.file, KtCallExpression::class.java)!!
        assertNull(ArmeriaKotlinClientCollector.protocolForCall(call))
        val name = kotlinCallName(call)
        val leaf = name.firstChild ?: name
        assertNull(kotlinProvider.getLineMarkerInfo(leaf))
    }

    private fun kotlinCallName(call: KtCallExpression): com.intellij.psi.PsiElement {
        val callee = call.calleeExpression
        return when (callee) {
            is KtDotQualifiedExpression -> callee.selectorExpression as KtNameReferenceExpression
            is KtNameReferenceExpression -> callee
            else -> error("unexpected callee $callee")
        }
    }
}
