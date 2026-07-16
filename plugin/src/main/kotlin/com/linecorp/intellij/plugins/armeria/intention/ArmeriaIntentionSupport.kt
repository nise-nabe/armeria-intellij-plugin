package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteSupport

internal object ArmeriaIntentionSupport {
    fun annotatedServiceClass(element: PsiElement): PsiClass? {
        val serviceClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false) ?: return null
        return serviceClass.takeIf(::isAnnotatedServiceCandidate)
    }

    fun isInsideClassBody(element: PsiElement, serviceClass: PsiClass): Boolean {
        val bodyStart = serviceClass.lBrace?.textRange?.startOffset ?: return false
        val bodyEnd = serviceClass.rBrace?.textRange?.endOffset ?: return false
        val offset = element.textRange.startOffset
        return offset in bodyStart..bodyEnd
    }

    fun suggestMethodName(serviceClass: PsiClass, baseName: String): String {
        val usedNames = serviceClass.methods.mapTo(linkedSetOf()) { it.name }
        var candidate = baseName
        var suffix = 2
        while (candidate in usedNames) {
            candidate = "$baseName$suffix"
            suffix++
        }
        return candidate
    }

    /** Method-relative path only; class `@PathPrefix` is composed at runtime. */
    fun suggestPath(methodName: String): String =
        ArmeriaRouteSupport.normalizePath("/$methodName")

    private fun isAnnotatedServiceCandidate(serviceClass: PsiClass): Boolean {
        if (serviceClass.isInterface || serviceClass.isEnum || serviceClass.isAnnotationType) {
            return false
        }
        if (serviceClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION) != null) {
            return true
        }
        return serviceClass.methods.any { ArmeriaRouteSupport.findRouteAnnotation(it) != null }
    }
}
