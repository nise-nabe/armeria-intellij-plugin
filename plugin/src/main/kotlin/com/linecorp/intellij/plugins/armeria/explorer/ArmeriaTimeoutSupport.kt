package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaTimeoutSupport {
    const val BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.Blocking"
    const val NON_BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.NonBlocking"

    fun collectTimeoutHints(element: PsiElement): List<String> {
        val hints = mutableListOf<String>()
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (method != null) {
            annotationHint(method, BLOCKING_ANNOTATION, "route.explorer.timeout.blocking")?.let(hints::add)
            annotationHint(method, NON_BLOCKING_ANNOTATION, "route.explorer.timeout.nonBlocking")?.let(hints::add)
            PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java).forEach { call ->
                when (call.methodExpression.referenceName) {
                    "requestTimeout" -> hints += formatTimeoutCall("route.explorer.timeout.request", call)
                    "responseTimeout" -> hints += formatTimeoutCall("route.explorer.timeout.response", call)
                    "idleTimeout" -> hints += formatTimeoutCall("route.explorer.timeout.idle", call)
                }
            }
        }
        return hints.distinct()
    }

    private fun annotationHint(method: PsiMethod, fqn: String, labelKey: String): String? {
        return if (method.hasAnnotation(fqn)) message(labelKey) else null
    }

    private fun formatTimeoutCall(labelKey: String, call: PsiMethodCallExpression): String {
        val value = call.argumentList.expressions.firstOrNull()?.text ?: "…"
        return message("route.explorer.timeout.value", message(labelKey), value)
    }
}
