package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.explorer.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaRouteLineMarkerSupport {
    val SERVICE_REGISTRATION_METHODS = ServiceRegistrationMethod.CORE_METHOD_NAMES

    fun createMarker(element: PsiElement, tooltip: String): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            element,
            element.textRange,
            ArmeriaIcons.Armeria,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { message("marker.route.title") },
        )
    }
}
