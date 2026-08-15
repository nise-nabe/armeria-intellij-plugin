package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteLinkSupport
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteNavigation
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import java.awt.event.MouseEvent

internal class ArmeriaKotlinClientRouteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (DumbService.isDumb(element.project)) {
            return null
        }
        return try {
            kotlinClientRouteMarker(element)
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun kotlinClientRouteMarker(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) {
            return null
        }
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false) ?: return null
        val referenceNameElement = kotlinCallReferenceNameElement(call) ?: return null
        if (element != referenceNameElement && element.parent != referenceNameElement) {
            return null
        }
        val methodName = ArmeriaKotlinExpressionSupport.resolveCallName(call) ?: return null
        if (methodName !in ArmeriaClientSupport.FACTORY_METHOD_NAMES &&
            methodName !in ArmeriaClientSupport.CONVERSION_METHOD_NAMES
        ) {
            return null
        }
        val endpoint = ArmeriaClientRouteNavigation.endpointForCall(call) ?: return null
        val routes = ArmeriaClientRouteLinkSupport.matchingRoutes(element.project, endpoint)
        if (routes.isEmpty()) {
            return null
        }
        val first = routes.first()
        val tooltip =
            if (routes.size == 1) {
                message("marker.client.route.tooltip", first.methodLabel, first.path)
            } else {
                message("marker.client.route.tooltipMultiple", routes.size)
            }
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

    private fun kotlinCallReferenceNameElement(call: KtCallExpression): PsiElement? =
        when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.selectorExpression
            is KtNameReferenceExpression -> callee
            else -> null
        }
}
