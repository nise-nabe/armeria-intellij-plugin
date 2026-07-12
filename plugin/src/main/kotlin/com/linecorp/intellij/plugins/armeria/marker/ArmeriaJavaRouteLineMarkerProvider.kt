package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.editor.ArmeriaRouteNavigationSupport
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaJavaRouteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) {
            return null
        }
        if (DumbService.isDumb(element.project)) {
            return null
        }
        return try {
            annotatedJavaMarker(element) ?: javaServiceRegistrationMarker(element)
        } catch (_: IndexNotReadyException) {
            null
        }
    }

    private fun annotatedJavaMarker(element: PsiElement): LineMarkerInfo<*>? {
        val method = (element.parent as? PsiMethod)
            ?: PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
            ?: return null
        if (element != method.nameIdentifier) {
            return null
        }
        val annotation = ArmeriaRouteNavigationSupport.httpMethod(method) ?: return null
        val path = ArmeriaRouteNavigationSupport.routePath(method)
        return ArmeriaRouteLineMarkerSupport.createMarker(
            method.nameIdentifier ?: method,
            message("marker.route.annotated", annotation, path),
        )
    }

    private fun javaServiceRegistrationMarker(element: PsiElement): LineMarkerInfo<*>? {
        val call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false) ?: return null
        if (element != call.methodExpression.referenceNameElement) {
            return null
        }
        val methodName = call.methodExpression.referenceName ?: return null
        if (methodName !in ArmeriaRouteLineMarkerSupport.SERVICE_REGISTRATION_METHODS) {
            return null
        }
        if (!ArmeriaRouteCollector.looksLikeArmeriaBuilderCall(call)) {
            return null
        }
        val path = javaRegistrationPath(methodName, call.argumentList.expressions.toList()) ?: "/"
        return ArmeriaRouteLineMarkerSupport.createMarker(
            element,
            message("marker.route.registration", methodName, path),
        )
    }

    companion object {
        internal fun javaRegistrationPath(methodName: String, expressions: List<PsiExpression>): String? {
            return when (methodName) {
                "annotatedService" -> {
                    if (expressions.size > 1) {
                        ArmeriaRouteSupport.extractJavaStringConstant(expressions[0])
                    } else {
                        "/"
                    }
                }
                else -> ArmeriaRouteSupport.extractJavaStringConstant(expressions.firstOrNull())
            }
        }
    }
}
