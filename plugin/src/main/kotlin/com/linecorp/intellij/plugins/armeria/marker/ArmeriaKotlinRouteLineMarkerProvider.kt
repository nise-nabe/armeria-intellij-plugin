package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.editor.ArmeriaRouteNavigationSupport
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument

internal class ArmeriaKotlinRouteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (DumbService.isDumb(element.project)) {
            return null
        }
        return try {
            annotatedKotlinMarker(element) ?: kotlinServiceRegistrationMarker(element)
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun annotatedKotlinMarker(element: PsiElement): LineMarkerInfo<*>? {
        val function = (element as? KtNamedFunction)
            ?: PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java, false)
            ?: return null
        if (element != function.nameIdentifier) {
            return null
        }
        val httpMethod = ArmeriaRouteNavigationSupport.httpMethod(function) ?: return null
        val path = ArmeriaRouteNavigationSupport.routePath(function)
        return ArmeriaRouteLineMarkerSupport.createMarker(
            function.nameIdentifier ?: function,
            message("marker.route.annotated", httpMethod, path),
        )
    }

    private fun kotlinServiceRegistrationMarker(element: PsiElement): LineMarkerInfo<*>? {
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false) ?: return null
        val referenceNameElement = kotlinCallReferenceNameElement(call) ?: return null
        if (!isOnReferenceName(element, referenceNameElement)) {
            return null
        }
        val methodName = kotlinCallName(call) ?: return null
        if (methodName !in ArmeriaRouteLineMarkerSupport.SERVICE_REGISTRATION_METHODS) {
            return null
        }
        if (!ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(call)) {
            return null
        }
        val path = kotlinRegistrationPath(methodName, call.valueArguments) ?: "/"
        return ArmeriaRouteLineMarkerSupport.createMarker(
            element,
            message("marker.route.registration", methodName, path),
        )
    }

    private fun isOnReferenceName(element: PsiElement, referenceName: PsiElement): Boolean {
        return element == referenceName || element.parent == referenceName
    }

    private fun kotlinCallReferenceNameElement(call: KtCallExpression): PsiElement? {
        return when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.selectorExpression
            is KtNameReferenceExpression -> callee
            else -> null
        }
    }

    private fun kotlinCallName(call: KtCallExpression): String? {
        return when (val callee = call.calleeExpression) {
            is KtDotQualifiedExpression -> callee.selectorExpression?.text
            is KtNameReferenceExpression -> callee.text
            else -> callee?.text
        }
    }

    private fun kotlinRegistrationPath(methodName: String, arguments: List<KtValueArgument>): String? {
        return when (ServiceRegistrationMethod.fromMethodName(methodName)) {
            ServiceRegistrationMethod.SERVICE ->
                ArmeriaKotlinExpressionSupport.extractKotlinString(arguments.firstOrNull()?.getArgumentExpression())
            ServiceRegistrationMethod.SERVICE_UNDER ->
                ArmeriaKotlinExpressionSupport.extractKotlinString(
                    arguments.firstOrNull { it.getArgumentName()?.asName?.identifier == "pathPrefix" }
                        ?.getArgumentExpression()
                        ?: arguments.firstOrNull()?.getArgumentExpression(),
                )
            ServiceRegistrationMethod.ANNOTATED_SERVICE ->
                if (arguments.size > 1) {
                    ArmeriaKotlinExpressionSupport.extractKotlinString(
                        arguments.firstOrNull { it.getArgumentName()?.asName?.identifier == "pathPrefix" }
                            ?.getArgumentExpression()
                            ?: arguments.firstOrNull()?.getArgumentExpression(),
                    )
                } else {
                    "/"
                }
            else -> null
        }
    }
}
