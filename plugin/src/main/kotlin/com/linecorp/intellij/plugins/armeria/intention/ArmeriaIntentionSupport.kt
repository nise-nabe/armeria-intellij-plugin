package com.linecorp.intellij.plugins.armeria.intention

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
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

    fun suggestPath(serviceClass: PsiClass, methodName: String): String {
        val classPrefix = ArmeriaRouteSupport.extractPrimaryPath(
            serviceClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION),
        )
        val path = if (classPrefix.isBlank()) "/$methodName" else "${classPrefix.trimEnd('/')}/$methodName"
        return ArmeriaRouteSupport.normalizePath(path)
    }

    private fun isAnnotatedServiceCandidate(serviceClass: PsiClass): Boolean {
        if (serviceClass.isInterface || serviceClass.isEnum || serviceClass.isAnnotationType) {
            return false
        }
        if (serviceClass.getAnnotation(ArmeriaRouteSupport.PATH_PREFIX_ANNOTATION) != null) {
            return true
        }
        if (serviceClass.methods.any { ArmeriaRouteSupport.findRouteAnnotation(it) != null }) {
            return true
        }
        val file = serviceClass.containingFile as? PsiJavaFile ?: return false
        return file.importList?.allImportStatements?.any { import ->
            import.text.contains("com.linecorp.armeria.server.annotation")
        } == true
    }
}
