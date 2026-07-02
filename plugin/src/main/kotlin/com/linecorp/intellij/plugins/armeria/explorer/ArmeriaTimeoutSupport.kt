package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil

internal object ArmeriaTimeoutSupport {
    const val BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.Blocking"
    const val NON_BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.NonBlocking"

    fun collectTimeoutHints(element: PsiElement): List<String> {
        val hints = mutableListOf<String>()
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        if (method != null) {
            annotationHint(method, BLOCKING_ANNOTATION, "Blocking")?.let(hints::add)
            annotationHint(method, NON_BLOCKING_ANNOTATION, "Non-blocking")?.let(hints::add)
        }
        PsiTreeUtil.findChildrenOfType(element.containingFile, PsiMethodCallExpression::class.java).forEach { call ->
            when (call.methodExpression.referenceName) {
                "requestTimeout" -> hints += formatTimeoutCall("Request timeout", call)
                "responseTimeout" -> hints += formatTimeoutCall("Response timeout", call)
                "idleTimeout" -> hints += formatTimeoutCall("Idle timeout", call)
            }
        }
        return hints.distinct()
    }

    private fun annotationHint(method: PsiMethod, fqn: String, label: String): String? {
        return if (method.hasAnnotation(fqn)) label else null
    }

    private fun formatTimeoutCall(label: String, call: PsiMethodCallExpression): String {
        val value = call.argumentList.expressions.firstOrNull()?.text ?: "…"
        return "$label: $value"
    }
}
