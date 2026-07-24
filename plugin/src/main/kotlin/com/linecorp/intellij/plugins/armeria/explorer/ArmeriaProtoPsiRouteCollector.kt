package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.protobuf.lang.psi.PbServiceDefinition
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaGrpcRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtoRouteCollector

/**
 * Proto Editor PSI-backed gRPC route collector. Loaded only when
 * `idea.plugin.protoeditor` is present (see `proto-integration.xml`).
 */
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
        for (service in services) {
            for (method in service.body?.serviceMethodList.orEmpty()) {
                val resolved = resolveProtoGrpcMethod(method) ?: continue
                ArmeriaGrpcRouteCollector.addProtoRoute(
                    method,
                    resolved.fqService,
                    resolved.methodName,
                    routes,
                    seenProtoRoutes,
                )
            }
        }
        // Services were present: PSI owns this file even when every method was skipped/deduped.
        return true
    }
}
