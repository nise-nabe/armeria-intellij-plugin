package com.linecorp.intellij.plugins.armeria.explorer.protocol

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

/**
 * Optional `.proto` route extractor. Implementations that depend on Proto Editor PSI
 * register via `proto-integration.xml` when `idea.plugin.protoeditor` is present.
 *
 * Returning `true` means the file was handled (routes may still be empty after dedupe).
 * Returning `false` lets [ArmeriaGrpcRouteCollector] fall back to text parsing.
 */
interface ArmeriaProtoRouteCollector {
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
