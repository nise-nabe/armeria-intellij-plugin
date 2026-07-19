package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import org.jetbrains.kotlin.psi.KtFile

/**
 * Core service-registration helpers that Spring Boot collectors need without depending on
 * collector implementation classes directly.
 */
interface CoreServiceRegistrationSupport {
    fun collectServiceRegistrationsInScope(
        element: PsiElement,
        routes: MutableList<ArmeriaRoute>,
        seen: MutableSet<String>,
    )

    fun collectServiceRegistrationFromMethodCall(
        expression: PsiMethodCallExpression,
        routes: MutableList<ArmeriaRoute>,
        seen: MutableSet<String>,
    )

    fun referencesArmeriaKotlinContent(file: KtFile): Boolean
}
