package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

internal class RouteRegistrationCallbacks(
    val collectServiceRegistrationsInScope: (PsiElement, MutableList<ArmeriaRoute>, MutableSet<String>) -> Unit,
    val collectServiceRegistrationFromMethodCall: (PsiMethodCallExpression, MutableList<ArmeriaRoute>, MutableSet<String>) -> Unit,
    /** Returns true if the given file references Armeria Kotlin content. Caller must ensure the file is a KtFile. */
    val referencesArmeriaKotlinContent: (PsiFile) -> Boolean,
)
