package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.message

data class ArmeriaRoute(
    val kind: String,
    val protocol: String,
    val httpMethod: String,
    val path: String,
    val target: String,
    val decorators: List<String>,
    val exceptionHandlers: List<String>,
    val pointer: SmartPsiElementPointer<PsiElement>,
) {
    val presentation: String
        get() = buildString {
            append("[$httpMethod] ")
            append(path)
            append("  ")
            append(target)
        }

    val secondaryPresentation: String
        get() = buildString {
            append(kind)
            append(message("route.explorer.secondary.separator"))
            append(protocol)
            if (decorators.isNotEmpty()) {
                append(message("route.explorer.secondary.decorators", decorators.joinToString()))
            }
            if (exceptionHandlers.isNotEmpty()) {
                append(message("route.explorer.secondary.handlers", exceptionHandlers.joinToString()))
            }
        }

    companion object {
        fun create(
            element: PsiElement,
            kind: String,
            protocol: String,
            httpMethod: String,
            path: String,
            target: String,
            decorators: List<String> = emptyList(),
            exceptionHandlers: List<String> = emptyList(),
        ): ArmeriaRoute {
            return ArmeriaRoute(
                kind = kind,
                protocol = protocol,
                httpMethod = httpMethod,
                path = path,
                target = target,
                decorators = decorators,
                exceptionHandlers = exceptionHandlers,
                pointer = SmartPointerManager.createPointer(element),
            )
        }
    }
}
