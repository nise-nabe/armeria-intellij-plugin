package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.DataKey
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

object ArmeriaRouteDataKeys {
    val SELECTED_ROUTE: DataKey<ArmeriaRoute> = DataKey.create("ARMERIA_SELECTED_ROUTE")
}
