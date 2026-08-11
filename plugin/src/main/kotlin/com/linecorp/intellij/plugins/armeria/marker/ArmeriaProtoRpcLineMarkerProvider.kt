package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.protobuf.lang.psi.ProtoTokenTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.resolveProtoGrpcMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaProtoRouteDiscoverySupport
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaProtoRpcLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.node?.elementType != ProtoTokenTypes.RPC) {
            return null
        }
        if (DumbService.isDumb(element.project)) {
            return null
        }
        val project = element.project
        return try {
            if (!ArmeriaProtoRouteDiscoverySupport.isEnabled()) {
                return null
            }
            if (!ArmeriaProtoRouteDiscoverySupport.isGrpcOnClasspath(project, GlobalSearchScope.projectScope(project))) {
                return null
            }
            protoRpcMarker(element)
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun protoRpcMarker(element: PsiElement): LineMarkerInfo<*>? {
        val method = element.parent as? PbServiceMethod ?: return null
        val resolved = resolveProtoGrpcMethod(method) ?: return null
        return ArmeriaRouteLineMarkerSupport.createGrpcMarker(
            element,
            message("marker.grpc.rpc", resolved.path),
        )
    }
}
