package com.linecorp.intellij.plugins.armeria.run

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Resolves JVM main classes from Kotlin sources, including top-level `fun main()`
 * (file facade such as `MainKt`) and mains declared on classes/objects.
 */
internal object ArmeriaKotlinMainClassSupport {
    fun findMainClass(element: PsiElement): PsiClass? {
        val ktFile = element.containingFile as? KtFile ?: return null

        PsiTreeUtil.getParentOfType(element, false, KtClassOrObject::class.java)
            ?.toLightClass()
            ?.takeIf(PsiMethodUtil::hasMainInClass)
            ?.let { return it }

        if (!hasTopLevelMain(ktFile)) {
            return null
        }
        val facade = ktFile.findFacadeClass() ?: return null
        return facade.takeIf { PsiMethodUtil.hasMainMethod(it) || PsiMethodUtil.hasMainInClass(it) }
    }

    private fun hasTopLevelMain(file: KtFile): Boolean =
        file.declarations.any { declaration ->
            declaration is KtNamedFunction &&
                declaration.isTopLevel &&
                declaration.name == "main"
        }
}
