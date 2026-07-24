package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.protobuf.lang.psi.PbServiceDefinition
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaGrpcRouteCollector
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaProtoRpcLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.text != "rpc") {
            return null
        }
        if (DumbService.isDumb(element.project)) {
            return null
        }
        return try {
            protoRpcMarker(element)
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun protoRpcMarker(element: PsiElement): LineMarkerInfo<*>? {
        val method = element.parent as? PbServiceMethod ?: return null
        val methodName = method.name ?: return null
        val service = PsiTreeUtil.getParentOfType(method, PbServiceDefinition::class.java) ?: return null
        val fqService = service.qualifiedName?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val path = ArmeriaGrpcRouteCollector.grpcPath(fqService, methodName)
        return ArmeriaRouteLineMarkerSupport.createGrpcMarker(
            element,
            message("marker.grpc.rpc", path),
        )
    }
}
