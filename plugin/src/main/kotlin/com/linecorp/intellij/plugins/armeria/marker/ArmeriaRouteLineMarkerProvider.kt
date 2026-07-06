package com.linecorp.intellij.plugins.armeria.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.ArmeriaIcons
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinExpressionSupport
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaKotlinRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.ServiceRegistrationMethod
import com.linecorp.intellij.plugins.armeria.inspection.ArmeriaKotlinMethodRoute
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument

internal class ArmeriaRouteLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return annotatedJavaMarker(element)
            ?: annotatedKotlinMarker(element)
            ?: kotlinServiceRegistrationMarker(element)
            ?: javaServiceRegistrationMarker(element)
    }

    private fun annotatedJavaMarker(element: PsiElement): LineMarkerInfo<*>? {
        val method = (element as? PsiMethod)
            ?: PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
            ?: return null
        if (element != method.nameIdentifier) {
            return null
        }
        val annotation = ArmeriaRouteSupport.findRouteAnnotation(method) ?: return null
        val classPrefix = ArmeriaRouteSupport.extractPrimaryPath(
            method.containingClass?.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
        )
        val path = ArmeriaRouteSupport.combinePaths(
            classPrefix,
            ArmeriaRouteSupport.extractPrimaryPath(annotation.first).ifBlank { "/" },
        )
        return createMarker(method.nameIdentifier ?: method, message("marker.route.annotated", annotation.second, path))
    }

    private fun annotatedKotlinMarker(element: PsiElement): LineMarkerInfo<*>? {
        val function = (element as? KtNamedFunction)
            ?: PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java, false)
            ?: return null
        if (element != function.nameIdentifier) {
            return null
        }
        val route = ArmeriaKotlinMethodRoute.from(function) ?: return null
        val path = route.paths.firstOrNull().orEmpty()
        return createMarker(function.nameIdentifier ?: function, message("marker.route.annotated", route.httpMethod, path))
    }

    private fun kotlinServiceRegistrationMarker(element: PsiElement): LineMarkerInfo<*>? {
        val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java, false) ?: return null
        val referenceNameElement = kotlinCallReferenceNameElement(call) ?: return null
        if (!isOnReferenceName(element, referenceNameElement)) {
            return null
        }
        val methodName = kotlinCallName(call) ?: return null
        if (methodName !in SERVICE_REGISTRATION_METHODS) {
            return null
        }
        if (!ArmeriaKotlinRouteCollector.looksLikeArmeriaBuilderCall(call)) {
            return null
        }
        val path = kotlinRegistrationPath(methodName, call.valueArguments) ?: "/"
        return createMarker(element, message("marker.route.registration", methodName, path))
    }

    private fun javaServiceRegistrationMarker(element: PsiElement): LineMarkerInfo<*>? {
        val call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression::class.java, false) ?: return null
        if (element != call.methodExpression.referenceNameElement) {
            return null
        }
        val methodName = call.methodExpression.referenceName ?: return null
        if (methodName !in SERVICE_REGISTRATION_METHODS) {
            return null
        }
        if (!ArmeriaRouteCollector.looksLikeArmeriaBuilderCall(call)) {
            return null
        }
        val path = when (methodName) {
            "annotatedService" -> {
                val args = call.argumentList.expressions
                if (args.size > 1) args[0].text.trim('"') else "/"
            }
            else -> call.argumentList.expressions.firstOrNull()?.text?.trim('"') ?: "/"
        }
        return createMarker(element, message("marker.route.registration", methodName, path))
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

    private fun createMarker(element: PsiElement, tooltip: String): LineMarkerInfo<PsiElement> {
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

    companion object {
        private val SERVICE_REGISTRATION_METHODS = ServiceRegistrationMethod.METHOD_NAMES
    }
}
