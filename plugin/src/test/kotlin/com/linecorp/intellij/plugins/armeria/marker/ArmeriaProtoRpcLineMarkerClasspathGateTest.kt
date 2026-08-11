package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertNull

class ArmeriaProtoRpcLineMarkerClasspathGateTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    private val provider = ArmeriaProtoRpcLineMarkerProvider()

    fun testProtoRpcMarkerHiddenWithoutGrpcOnClasspath() {
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

        assertNull(provider.getLineMarkerInfo(findRpcKeyword()))
    }

    private fun findRpcKeyword(): PsiElement {
        val method = PsiTreeUtil.findChildOfType(myFixture.file, PbServiceMethod::class.java)!!
        return myFixture.file.findElementAt(method.textRange.startOffset)!!
    }
}
