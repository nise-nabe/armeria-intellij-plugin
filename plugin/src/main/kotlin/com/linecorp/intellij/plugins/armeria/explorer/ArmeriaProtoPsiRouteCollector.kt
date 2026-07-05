package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceDefinition
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal class ArmeriaProtoPsiRouteCollector : ArmeriaProtoRouteCollector {
  override fun collectFromFile(
    file: PsiFile,
    routes: MutableList<ArmeriaRoute>,
    seenProtoRoutes: MutableSet<String>,
  ): Boolean {
    val pbFile = file as? PbFile ?: return false
    for (service in PsiTreeUtil.findChildrenOfType(pbFile, PbServiceDefinition::class.java)) {
      val fqService = service.qualifiedName?.toString().orEmpty()
      if (fqService.isBlank()) {
        continue
      }
      val methods = service.body?.serviceMethodList.orEmpty()
      for (method in methods) {
        val methodName = method.name ?: continue
        ArmeriaGrpcRouteCollector.addProtoRoute(method, fqService, methodName, routes, seenProtoRoutes)
      }
    }
    return true
  }
}
