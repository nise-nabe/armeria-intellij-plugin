package com.linecorp.intellij.plugins.armeria.explorer.model

object GrpcRoutePath {
    private val METHOD_PATH = Regex("""^/[^/]+/[^/]+$""")

    fun path(
        fqService: String,
        methodName: String,
    ): String = "/$fqService/$methodName"

    fun isMethodPath(path: String): Boolean = METHOD_PATH.matches(path)
}
