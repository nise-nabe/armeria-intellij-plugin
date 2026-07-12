package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import org.jetbrains.kotlin.psi.KtNamedFunction

class ArmeriaRouteLineMarkerProviderTest : LightJavaCodeInsightFixtureTestCase() {
    private val provider = ArmeriaRouteLineMarkerProvider()

    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    fun testJavaAnnotatedRouteMarker() {
        myFixture.configureByText(
            "Service.java",
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

        val method = PsiTreeUtil.findChildOfType(myFixture.file, PsiMethod::class.java)!!
        val marker = provider.getLineMarkerInfo(method.nameIdentifier!!)

        assertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker!!.icon)
    }

    fun testKotlinAnnotatedRouteMarker() {
        myFixture.configureByText(
            "Service.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class Service {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val function = PsiTreeUtil.findChildOfType(myFixture.file, KtNamedFunction::class.java)!!
        val marker = provider.getLineMarkerInfo(function.nameIdentifier!!)

        assertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker!!.icon)
    }

    fun testServiceRegistrationMarker() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new Object())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val fileText = myFixture.file.text
        val serviceIndex = fileText.indexOf("service")
        val element = myFixture.file.findElementAt(serviceIndex)!!
        val marker = provider.getLineMarkerInfo(element)

        assertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker!!.icon)
    }

    fun testKotlinServiceRegistrationMarker() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", Any())
                    .build()
            }
            """.trimIndent(),
        )

        val fileText = myFixture.file.text
        val serviceIndex = fileText.indexOf("service")
        val element = myFixture.file.findElementAt(serviceIndex)!!
        val marker = provider.getLineMarkerInfo(element)

        assertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker!!.icon)
    }

    fun testExtendedRegistrationMethodsDoNotGetMarkers() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .virtualHost("example.com")
                        .build();
                }
            }
            """.trimIndent(),
        )

        val fileText = myFixture.file.text
        val virtualHostIndex = fileText.indexOf("virtualHost")
        val element = myFixture.file.findElementAt(virtualHostIndex)!!
        val marker = provider.getLineMarkerInfo(element)

        assertNull(marker)
    }

    private fun registerArmeriaStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public class Server {
                public static ServerBuilder builder() {
                    return new ServerBuilder();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public class ServerBuilder {
                public ServerBuilder service(String path, Object handler) {
                    return this;
                }
                public ServerBuilder virtualHost(String hostname) {
                    return this;
                }
                public Server build() {
                    return new Server();
                }
            }
            """.trimIndent(),
        )
    }
}
