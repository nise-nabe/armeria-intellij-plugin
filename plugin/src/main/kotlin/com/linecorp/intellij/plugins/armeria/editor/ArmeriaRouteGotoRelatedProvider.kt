package com.linecorp.intellij.plugins.armeria.editor

import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.psi.PsiElement

class ArmeriaRouteGotoRelatedProvider : GotoRelatedProvider() {
    override fun getItems(context: PsiElement): List<GotoRelatedItem> {
        ArmeriaRouteNavigationSupport.annotatedRouteMethod(context)?.let { return ArmeriaRouteNavigationSupport.relatedGotoItems(it) }
        return ArmeriaRouteNavigationSupport.relatedHandlerGotoItems(context)
    }
}
