package com.linecorp.intellij.plugins.armeria.springboot.config

data class ArmeriaSpringBootConfigEntry(val key: String, val value: String, val documentation: String? = ArmeriaSpringBootConfigKeys.documentationFor(key))
data class ArmeriaSpringBootConfigFile(val fileName: String, val filePath: String, val entries: List<ArmeriaSpringBootConfigEntry>)
