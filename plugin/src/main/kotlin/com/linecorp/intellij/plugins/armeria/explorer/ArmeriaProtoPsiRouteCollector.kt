package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceDefinition
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class ArmeriaProtoPsiRouteCollector : ArmeriaProtoRouteCollector {
  override fun collectFromFile(
    file: PsiFile,
    routes: MutableList<ArmeriaRoute>,
    seenProtoRoutes: MutableSet<String>,
  ): Boolean {
    val pbFile = file as? PbFile ?: return false
    val services = PsiTreeUtil.findChildrenOfType(pbFile, PbServiceDefinition::class.java)
    if (services.isEmpty()) {
      return false
    }
    var collected = false
    for (service in services) {
      val fqService = service.qualifiedName?.toString().orEmpty()
      if (fqService.isBlank()) {
        return false
      }
      val methods = service.body?.serviceMethodList.orEmpty()
      for (method in methods) {
        val methodName = method.name ?: continue
        registerArmeriaGrpcProtoRoute(method, fqService, methodName, routes, seenProtoRoutes)
        collected = true
      }
    }
    return collected
  }
}
