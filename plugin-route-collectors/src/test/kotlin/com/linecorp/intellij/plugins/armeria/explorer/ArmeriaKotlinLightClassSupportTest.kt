package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.support.toArmeriaLightMethods
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.test.assertEquals

class ArmeriaKotlinLightClassSupportTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        // Light-class resolution does not need Armeria stubs.
    }

    fun testResolvesLightMethodsForKotlinDeclarationShapes() {
        val file =
            myFixture.configureByText(
                "Shapes.kt",
                """
                package example

                fun topLevelFunction() {}

                object Holder {
                    fun objectMember() {}
                }

                class Outer {
                    companion object {
                        @JvmStatic
                        fun companionMember() {}
                    }
                }
                """.trimIndent(),
            ) as KtFile
        val functions = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)

        assertEquals(1, functions.single { it.name == "topLevelFunction" }.toArmeriaLightMethods().size)
        assertEquals(1, functions.single { it.name == "objectMember" }.toArmeriaLightMethods().size)
        assertEquals(1, functions.single { it.name == "companionMember" }.toArmeriaLightMethods().size)
    }
}
