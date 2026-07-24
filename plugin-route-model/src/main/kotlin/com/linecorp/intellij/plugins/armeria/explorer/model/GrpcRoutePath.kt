package com.linecorp.intellij.plugins.armeria.explorer.model

object GrpcRoutePath {
    fun path(
        fqService: String,
        methodName: String,
    ): String = "/$fqService/$methodName"
}
