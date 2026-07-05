package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

internal interface ArmeriaProtoRouteCollector {
  fun collectFromFile(
    file: PsiFile,
    routes: MutableList<ArmeriaRoute>,
    seenProtoRoutes: MutableSet<String>,
  ): Boolean

  companion object {
    val EP: ExtensionPointName<ArmeriaProtoRouteCollector> =
      ExtensionPointName.create("com.linecorp.intellij.armeria.protoRouteCollector")
  }
}
