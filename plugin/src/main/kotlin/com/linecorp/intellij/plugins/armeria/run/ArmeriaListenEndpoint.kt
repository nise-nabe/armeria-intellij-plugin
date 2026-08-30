package com.linecorp.intellij.plugins.armeria.run

data class ArmeriaListenEndpoint(
    val port: Int,
    val https: Boolean = false,
)
