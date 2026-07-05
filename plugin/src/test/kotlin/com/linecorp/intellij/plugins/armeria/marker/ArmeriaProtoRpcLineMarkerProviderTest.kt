package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons

class ArmeriaProtoRpcLineMarkerProviderTest : LightJavaCodeInsightFixtureTestCase() {
    private val provider = ArmeriaProtoRpcLineMarkerProvider()

    fun testProtoRpcMarker() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val marker = findMarker(myFixture.file)

        assertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker!!.icon)
    }

    private fun findMarker(file: PsiElement): com.intellij.codeInsight.daemon.LineMarkerInfo<*>? {
        var result: com.intellij.codeInsight.daemon.LineMarkerInfo<*>? = null
        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (result == null) {
                    result = provider.getLineMarkerInfo(element)
                }
                super.visitElement(element)
            }
        })
        return result
    }
}
