package com.linecorp.intellij.plugins.armeria.springboot.config

data class ArmeriaSpringBootConfigEntry(
    val key: String,
    val value: String,
)

data class ArmeriaSpringBootConfigFile(
    val fileName: String,
    val filePath: String,
    val entries: List<ArmeriaSpringBootConfigEntry>,
)
