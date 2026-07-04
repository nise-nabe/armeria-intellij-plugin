package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaTimeoutSupport {
    const val BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.Blocking"
    const val NON_BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.NonBlocking"

    fun collectExecutionHints(element: PsiElement): List<String> {
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return emptyList()
        return buildList {
            annotationHint(method, BLOCKING_ANNOTATION, "route.explorer.timeout.blocking")?.let(::add)
            annotationHint(method, NON_BLOCKING_ANNOTATION, "route.explorer.timeout.nonBlocking")?.let(::add)
        }
    }

    fun collectTimeoutHints(element: PsiElement): List<String> {
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java)
            .mapNotNull { call ->
                when (call.methodExpression.referenceName) {
                    "requestTimeout" -> formatTimeoutCall("route.explorer.timeout.request", call)
                    "responseTimeout" -> formatTimeoutCall("route.explorer.timeout.response", call)
                    "idleTimeout" -> formatTimeoutCall("route.explorer.timeout.idle", call)
                    else -> null
                }
            }
            .distinct()
    }

    private fun annotationHint(method: PsiMethod, fqn: String, labelKey: String): String? {
        return if (method.hasAnnotation(fqn)) message(labelKey) else null
    }

    private fun formatTimeoutCall(labelKey: String, call: PsiMethodCallExpression): String? {
        val value = call.argumentList.expressions.firstOrNull()?.text ?: return null
        return message("route.explorer.timeout.value", message(labelKey), value)
    }
}
