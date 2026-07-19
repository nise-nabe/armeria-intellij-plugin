package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtoTextSupport
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaProtoTextSupportTest {
    @Test
    fun stripCommentsPreservesHttpsUrlInStringLiteral() {
        val proto =
            """
            rpc SayHello(HelloRequest) returns (HelloResponse) {
              option (google.api.http) = {
                post: "https://api.example.com/v1/hello"
              };
            }
            """.trimIndent()

        val stripped = ArmeriaProtoTextSupport.stripComments(proto)

        assertEquals(proto, stripped)
    }

    @Test
    fun stripCommentsDoesNotInsertSpaceBeforePunctuationAfterBlockComment() {
        val proto = "package com/*c*/.example;"

        val stripped = ArmeriaProtoTextSupport.stripComments(proto)

        assertEquals("package com.example;", stripped)
    }

    @Test
    fun stripCommentsInsertsSpaceBetweenIdentifiersSeparatedByBlockComment() {
        val proto = "rpc/*inline*/SayHello"

        val stripped = ArmeriaProtoTextSupport.stripComments(proto)

        assertEquals("rpc SayHello", stripped)
    }

    @Test
    fun findMatchingCloseBraceIgnoresBracesInsideStringLiterals() {
        val text = """service Greeter { rpc SayHello() { option path = "/v1/{id"; } }"""

        val openBrace = text.indexOf('{')
        val closeBrace = ArmeriaProtoTextSupport.findMatchingCloseBrace(text, openBrace)

        assertEquals(text.lastIndexOf('}'), closeBrace)
    }
}
