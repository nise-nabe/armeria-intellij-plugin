package com.linecorp.intellij.plugins.armeria.client

data class ArmeriaClientEndpointGroup(
    val moduleName: String,
    val sourceKey: String,
    val endpoints: List<ArmeriaClientEndpoint>,
)
