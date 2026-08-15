package com.linecorp.intellij.plugins.armeria.explorer.endpoints

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

data class ArmeriaEndpointGroup(
    val moduleName: String,
    val sourceKey: String,
    val routes: List<ArmeriaRoute>,
)
