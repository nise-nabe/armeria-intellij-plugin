package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.explorer.model.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaRouteLineMarkerSupport {
    val SERVICE_REGISTRATION_METHODS = ServiceRegistrationMethod.CORE_METHOD_NAMES

    fun createMarker(
        element: PsiElement,
        tooltip: String,
    ): LineMarkerInfo<PsiElement> = createMarker(element, tooltip, "marker.route.title")

    fun createGrpcMarker(
        element: PsiElement,
        tooltip: String,
    ): LineMarkerInfo<PsiElement> = createMarker(element, tooltip, "marker.grpc.title")

    private fun createMarker(
        element: PsiElement,
        tooltip: String,
        titleKey: String,
    ): LineMarkerInfo<PsiElement> =
        LineMarkerInfo(
            element,
            element.textRange,
            ArmeriaIcons.Armeria,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { message(titleKey) },
        )
}
