package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase

class ArmeriaProtoRpcLineMarkerProviderTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    private val provider = ArmeriaProtoRpcLineMarkerProvider()

    fun testProtoRpcMarkerShowsGrpcPath() {
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
        assertTrue(myFixture.file is PbFile)

        val rpcKeyword = findRpcKeyword()
        val marker = provider.getLineMarkerInfo(rpcKeyword)

        assertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker!!.icon)
        assertEquals(
            message("marker.grpc.rpc", "/com.example.Greeter/SayHello"),
            marker.lineMarkerTooltip,
        )
    }

    fun testProtoRpcMarkerWithoutPackage() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";

            service Greeter {
              rpc Ping(PingRequest) returns (PingResponse);
            }
            """.trimIndent(),
        )
        assertTrue(myFixture.file is PbFile)

        val rpcKeyword = findRpcKeyword()
        val marker = provider.getLineMarkerInfo(rpcKeyword)

        assertNotNull(marker)
        assertEquals(
            message("marker.grpc.rpc", "/Greeter/Ping"),
            marker!!.lineMarkerTooltip,
        )
    }

    fun testServiceKeywordHasNoMarker() {
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

        val serviceIndex = myFixture.file.text.indexOf("service")
        val serviceKeyword = myFixture.file.findElementAt(serviceIndex)!!

        assertNull(provider.getLineMarkerInfo(serviceKeyword))
    }

    fun testMethodNameIdentifierHasNoMarker() {
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

        val method = PsiTreeUtil.findChildOfType(myFixture.file, PbServiceMethod::class.java)!!
        val nameIdentifier = method.nameIdentifier!!

        assertNull(provider.getLineMarkerInfo(nameIdentifier))
    }

    fun testProtoRpcMarkerIgnoresJavaPackageOption() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;
            option java_package = "com.example.java";

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val marker = provider.getLineMarkerInfo(findRpcKeyword())

        assertNotNull(marker)
        assertEquals(
            message("marker.grpc.rpc", "/com.example.Greeter/SayHello"),
            marker!!.lineMarkerTooltip,
        )
    }

    fun testCommentedRpcHasNoMarker() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              // rpc Deprecated(HelloRequest) returns (HelloResponse);
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }
            """.trimIndent(),
        )

        val commentedRpcIndex = myFixture.file.text.indexOf("// rpc")
        val commentedRpcKeyword = myFixture.file.findElementAt(commentedRpcIndex + 3)!!

        assertNull(provider.getLineMarkerInfo(commentedRpcKeyword))
        assertNotNull(provider.getLineMarkerInfo(findRpcKeyword()))
    }

    private fun findRpcKeyword(): PsiElement {
        val method = PsiTreeUtil.findChildOfType(myFixture.file, PbServiceMethod::class.java)!!
        return myFixture.file.findElementAt(method.textRange.startOffset)!!
    }
}
