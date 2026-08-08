package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.openapi.util.registry.Registry
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

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

        kotlinAssertNotNull(marker)
        assertEquals(ArmeriaIcons.Armeria, marker.icon)
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

        kotlinAssertNotNull(marker)
        assertEquals(
            message("marker.grpc.rpc", "/Greeter/Ping"),
            marker.lineMarkerTooltip,
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

        kotlinAssertNotNull(marker)
        assertEquals(
            message("marker.grpc.rpc", "/com.example.Greeter/SayHello"),
            marker.lineMarkerTooltip,
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
        kotlinAssertNotNull(provider.getLineMarkerInfo(findRpcKeyword()))
    }

    fun testMultipleRpcMarkersInOneService() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
              rpc SayGoodbye(GoodbyeRequest) returns (GoodbyeResponse);
            }
            """.trimIndent(),
        )

        val methods = PsiTreeUtil.findChildrenOfType(myFixture.file, PbServiceMethod::class.java).toList()
        assertEquals(2, methods.size)

        val markers =
            methods.map { method ->
                val rpcKeyword = myFixture.file.findElementAt(method.textRange.startOffset)!!
                provider.getLineMarkerInfo(rpcKeyword)
            }
        assertEquals(2, markers.filterNotNull().size)
        assertEquals(
            listOf(
                message("marker.grpc.rpc", "/com.example.Greeter/SayGoodbye"),
                message("marker.grpc.rpc", "/com.example.Greeter/SayHello"),
            ),
            markers.mapNotNull { it?.lineMarkerTooltip }.sorted(),
        )
    }

    fun testMultipleServicesEachGetRpcMarkers() {
        myFixture.configureByText(
            "multi.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc SayHello(HelloRequest) returns (HelloResponse);
            }

            service Echo {
              rpc Ping(PingRequest) returns (PingResponse);
            }
            """.trimIndent(),
        )

        val methods = PsiTreeUtil.findChildrenOfType(myFixture.file, PbServiceMethod::class.java).toList()
        assertEquals(2, methods.size)

        val tooltips =
            methods
                .map { method ->
                    val rpcKeyword = myFixture.file.findElementAt(method.textRange.startOffset)!!
                    provider.getLineMarkerInfo(rpcKeyword)!!.lineMarkerTooltip
                }.sorted()
        assertEquals(
            listOf(
                message("marker.grpc.rpc", "/com.example.Echo/Ping"),
                message("marker.grpc.rpc", "/com.example.Greeter/SayHello"),
            ),
            tooltips,
        )
    }

    fun testProtoRpcMarkerDisabledWhenRegistryOff() {
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

        val registryKey = Registry.get("armeria.grpc.proto.routes.enabled")
        val original = registryKey.asBoolean()
        try {
            registryKey.setValue(false)
            assertNull(provider.getLineMarkerInfo(findRpcKeyword()))
        } finally {
            registryKey.setValue(original)
        }
    }

    fun testStreamingRpcMarkerShowsGrpcPath() {
        myFixture.configureByText(
            "greeter.proto",
            """
            syntax = "proto3";
            package com.example;

            service Greeter {
              rpc StreamHello(stream HelloRequest) returns (stream HelloResponse);
            }
            """.trimIndent(),
        )

        val marker = provider.getLineMarkerInfo(findRpcKeyword())

        kotlinAssertNotNull(marker)
        assertEquals(
            message("marker.grpc.rpc", "/com.example.Greeter/StreamHello"),
            marker.lineMarkerTooltip,
        )
    }

    private fun findRpcKeyword(): PsiElement {
        val method = PsiTreeUtil.findChildOfType(myFixture.file, PbServiceMethod::class.java)!!
        return myFixture.file.findElementAt(method.textRange.startOffset)!!
    }
}
