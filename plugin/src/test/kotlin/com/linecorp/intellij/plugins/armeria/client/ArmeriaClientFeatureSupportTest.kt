package com.linecorp.intellij.plugins.armeria.client

import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaClientFeatureSupportTest : LightJavaCodeInsightFixtureTestCase() {
    fun testExtractDecoratorFromBuilderChain() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.client.WebClient;
            import com.linecorp.armeria.client.logging.LoggingClient;

            public class Main {
                public static void main(String[] args) {
                    WebClient.builder("https://example.com")
                        .decorator(LoggingClient.newDecorator());
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client;

            public final class WebClient {
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
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.client.logging;

            public final class LoggingClient {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )

        val methodCall = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiMethodCallExpression::class.java)
            .first { it.methodExpression.referenceName == "builder" }
        val features = ArmeriaClientFeatureSupport.extractJavaFeatures(methodCall)

        assertTrue(features.any { it.contains("Logging") })
    }
}
