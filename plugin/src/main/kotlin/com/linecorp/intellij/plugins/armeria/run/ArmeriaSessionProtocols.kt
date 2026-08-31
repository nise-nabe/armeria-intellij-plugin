package com.linecorp.intellij.plugins.armeria.run

/**
 * Armeria session-protocol names used when classifying listen ports.
 * `H1` / `H2` are HTTP/1 and HTTP/2 over TLS; `H1C` / `H2C` are cleartext.
 */
internal object ArmeriaSessionProtocols {
    val CLEARTEXT = setOf("HTTP", "H1C", "H2C")
    val TLS = setOf("HTTPS", "H1", "H2")

    private val TLS_TOKEN = Regex("""\bHTTPS\b|\bH1\b|\bH2\b""")
    private val CLEARTEXT_TOKEN = Regex("""\bHTTP\b|\bH1C\b|\bH2C\b""")

    fun isHttpsOnly(protocol: String): Boolean {
        val protocols =
            protocol
                .split(',', '+')
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
        if (protocols.isEmpty()) {
            return false
        }
        val hasCleartext = protocols.any { it in CLEARTEXT }
        val hasTls = protocols.any { it in TLS }
        return hasTls && !hasCleartext
    }

    fun extraArgsSuggestHttps(blob: String): Boolean = TLS_TOKEN.containsMatchIn(blob) && !CLEARTEXT_TOKEN.containsMatchIn(blob)
}
