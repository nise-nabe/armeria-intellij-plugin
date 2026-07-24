package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.protobuf.lang.psi.PbServiceDefinition
import com.intellij.protobuf.lang.psi.PbServiceMethod
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaGrpcRouteCollector

internal data class ResolvedProtoGrpcMethod(
    val fqService: String,
    val methodName: String,
) {
    val path: String
        get() = ArmeriaGrpcRouteCollector.grpcPath(fqService, methodName)
}

internal fun resolveProtoGrpcMethod(method: PbServiceMethod): ResolvedProtoGrpcMethod? {
    val methodName = method.name ?: return null
    val service = PsiTreeUtil.getParentOfType(method, PbServiceDefinition::class.java) ?: return null
    val fqService = service.qualifiedName?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return ResolvedProtoGrpcMethod(fqService, methodName)
}
