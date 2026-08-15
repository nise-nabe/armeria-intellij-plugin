package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteLinkSupport
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteNavigation
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport
import com.linecorp.intellij.plugins.armeria.message
import java.awt.event.MouseEvent

internal class ArmeriaJavaClientRouteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) {
            return null
        }
        if (DumbService.isDumb(element.project)) {
            return null
        }
        return try {
            javaClientRouteMarker(element)
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun javaClientRouteMarker(element: PsiElement): LineMarkerInfo<*>? {
        val call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false) ?: return null
        if (element != call.methodExpression.referenceNameElement) {
            return null
        }
        val methodName = call.methodExpression.referenceName ?: return null
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
            methodName !in ArmeriaClientSupport.CONVERSION_METHOD_NAMES
        ) {
            return null
        }
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (ArmeriaClientSupport.protocolForInvocation(methodName, resolvedClass) == null) {
            return null
        }
        val endpoint = ArmeriaClientRouteNavigation.endpointForCall(call) ?: return null
        val routes = ArmeriaClientRouteLinkSupport.matchingRoutes(element.project, endpoint)
        if (routes.isEmpty()) {
            return null
        }
        val tooltip = ArmeriaClientRouteLinkSupport.matchingRouteTooltip(routes)
        return LineMarkerInfo(
            element,
            element.textRange,
            ArmeriaIcons.Armeria,
            { tooltip },
            GutterIconNavigationHandler { event: MouseEvent, _ ->
                ArmeriaClientRouteNavigation.openMatchingRoutes(
                    element.project,
                    endpoint,
                    mouseEvent = event,
                )
            },
            GutterIconRenderer.Alignment.CENTER,
            { message("marker.client.route.title") },
        )
    }
}
