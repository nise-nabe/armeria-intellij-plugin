package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
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
        val marker = kotlinProvider.getLineMarkerInfo(name)

        kotlinAssertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker.icon)
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
