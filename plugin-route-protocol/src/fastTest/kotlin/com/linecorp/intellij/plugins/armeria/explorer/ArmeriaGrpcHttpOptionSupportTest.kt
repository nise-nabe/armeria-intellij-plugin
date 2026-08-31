package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaGrpcHttpOptionSupport
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaGrpcHttpOptionSupportTest {
    @Test
    fun parseBindings_readsGetPostAndCustomPaths() {
        val source =
            """
            rpc SayHello(HelloRequest) returns (HelloResponse) {
              option (google.api.http) = {
                post: "/v1/hello"
                additional_bindings {
                  get: "/v1/hello/{name}"
                }
                additional_bindings {
                  custom: {
                    kind: "HEAD"
                    path: "/v1/hello/head"
                  }
                }
              };
            }
            """.trimIndent()

        val bindings = ArmeriaGrpcHttpOptionSupport.parseBindings(source)

        assertEquals(
            listOf("POST /v1/hello", "GET /v1/hello/{name}", "HEAD /v1/hello/head"),
            bindings.map { it.display },
        )
    }

    @Test
    fun parseBindings_keepsHttpsUrl() {
        val source =
            """
            option (google.api.http) = {
              post: "https://api.example.com/v1/hello"
            };
            """.trimIndent()

        val bindings = ArmeriaGrpcHttpOptionSupport.parseBindings(source)

        assertEquals(listOf("POST https://api.example.com/v1/hello"), bindings.map { it.display })
        assertTrue(ArmeriaGrpcHttpOptionSupport.contentHints(source).isNotEmpty())
    }

    @Test
    fun parseBindings_pairsEachCustomKindWithItsPath() {
        val source =
            """
            option (google.api.http) = {
              custom: {
                kind: "HEAD"
                path: "/v1/hello/head"
              }
              additional_bindings {
                custom: {
                  kind: "OPTIONS"
                  path: "/v1/hello/options"
                }
              }
            };
            """.trimIndent()

        assertEquals(
            listOf("HEAD /v1/hello/head", "OPTIONS /v1/hello/options"),
            ArmeriaGrpcHttpOptionSupport.parseBindings(source).map { it.display },
        )
    }

    @Test
    fun parseBindings_ignoresHttpPathsOutsideGoogleApiHttpOption() {
        val source =
            """
            rpc SayHello(HelloRequest) returns (HelloResponse) {
              option (other.api.option) = {
                post: "/not-transcoding"
              };
              option (google.api.http) = {
                get: "/v1/hello"
              };
            }
            """.trimIndent()

        assertEquals(listOf("GET /v1/hello"), ArmeriaGrpcHttpOptionSupport.parseBindings(source).map { it.display })
    }

    @Test
    fun parseBindings_ignoresBodyStar() {
        val source =
            """
            option (google.api.http) = {
              post: "/v1/hello"
              body: "*"
            };
            """.trimIndent()

        assertEquals(listOf("POST /v1/hello"), ArmeriaGrpcHttpOptionSupport.parseBindings(source).map { it.display })
    }
}
