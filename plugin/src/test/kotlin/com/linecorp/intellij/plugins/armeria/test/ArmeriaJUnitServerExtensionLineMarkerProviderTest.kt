package com.linecorp.intellij.plugins.armeria.test

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaJUnitServerExtensionLineMarkerProviderTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    private val javaProvider = ArmeriaJUnitServerExtensionLineMarkerProvider()
    private val kotlinProvider = ArmeriaKotlinJUnitServerExtensionLineMarkerProvider()

    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
    }

    fun testJavaRegisterExtensionFieldShowsRunMarker() {
        myFixture.configureByText(
            "ExampleServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class ExampleServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};
            }
            """.trimIndent(),
        )

        val field = PsiTreeUtil.findChildOfType(myFixture.file, PsiField::class.java)!!
        val marker = javaProvider.getLineMarkerInfo(field.nameIdentifier!!)

        kotlinAssertNotNull(marker)
        assertEquals(AllIcons.RunConfigurations.Junit, marker.icon)
        assertEquals(message("test.support.lineMarker.tooltip", "server"), marker.lineMarkerTooltip)
    }

    fun testKotlinRegisterExtensionPropertyShowsRunMarker() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val property = PsiTreeUtil.findChildOfType(myFixture.file, KtProperty::class.java)!!
        val marker = kotlinProvider.getLineMarkerInfo(property.nameIdentifier!!)

        kotlinAssertNotNull(marker)
        assertEquals(AllIcons.RunConfigurations.Junit, marker.icon)
        assertEquals(message("test.support.lineMarker.tooltip", "server"), marker.lineMarkerTooltip)
    }

    fun testNonExtensionFieldHasNoMarker() {
        myFixture.configureByText(
            "ExampleServiceTest.java",
            """
            package example;

            public class ExampleServiceTest {
                static String helper = "value";
            }
            """.trimIndent(),
        )

        val field = PsiTreeUtil.findChildOfType(myFixture.file, PsiField::class.java)!!
        assertNull(javaProvider.getLineMarkerInfo(field.nameIdentifier!!))
    }

    fun testNonExtensionKotlinPropertyHasNoMarker() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            class ExampleServiceTest {
                val helper: String = "value"
            }
            """.trimIndent(),
        )

        val property = PsiTreeUtil.findChildOfType(myFixture.file, KtProperty::class.java)!!
        assertNull(kotlinProvider.getLineMarkerInfo(property.nameIdentifier!!))
    }

    fun testJavaRegisterExtensionInMainSourceHasNoMarker() {
        myFixture.withTemporaryMainSourceRoot { mainRoot ->
            val content =
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class MisnamedTest {
                    @RegisterExtension
                    static ServerExtension server = new ServerExtension() {};
                }
                """.trimIndent()
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                    val file = mainRoot.createChildData(this, "MisnamedTest.java")
                    VfsUtil.saveText(file, content)
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
            val field = PsiTreeUtil.findChildOfType(psiFile, PsiField::class.java)!!

            assertNull(javaProvider.getLineMarkerInfo(field.nameIdentifier!!))
        }
    }

    fun testKotlinRegisterExtensionInMainSourceHasNoMarker() {
        myFixture.withTemporaryMainSourceRoot { mainRoot ->
            val content =
                """
                package example

                import org.junit.jupiter.api.extension.RegisterExtension
                import com.linecorp.armeria.testing.junit5.server.ServerExtension

                class MisnamedTest {
                    @RegisterExtension
                    val server: ServerExtension = object : ServerExtension() {}
                }
                """.trimIndent()
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<com.intellij.openapi.vfs.VirtualFile> {
                    val file = mainRoot.createChildData(this, "MisnamedTest.kt")
                    VfsUtil.saveText(file, content)
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
            val property = PsiTreeUtil.findChildOfType(psiFile, KtProperty::class.java)!!

            assertNull(kotlinProvider.getLineMarkerInfo(property.nameIdentifier!!))
        }
    }
}
