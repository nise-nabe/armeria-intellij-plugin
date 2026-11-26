package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration

/**
 * Centralizes Kotlin light-class construction so deprecated convenience helpers can be
 * replaced without changing resolution semantics.
 */
fun KtClassOrObject.toArmeriaLightClass(): KtLightClass? = KotlinAsJavaSupport.getInstance(project).getLightClass(this)

fun KtFile.findArmeriaFacadeClass(): KtLightClass? = KotlinAsJavaSupport.getInstance(project).getLightFacade(this)

fun KtNamedFunction.toArmeriaLightMethods(): List<PsiMethod> {
    val containingClass = PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java)
    val lightClasses =
        buildList {
            if (containingClass == null) {
                containingKtFile.findArmeriaFacadeClass()?.let(::add)
            } else {
                containingClass.toArmeriaLightClass()?.let(::add)
                if (containingClass is KtObjectDeclaration && containingClass.isCompanion()) {
                    PsiTreeUtil
                        .getParentOfType(containingClass, KtClassOrObject::class.java)
                        ?.toArmeriaLightClass()
                        ?.let(::add)
                }
            }
        }
    return lightClasses
        .flatMap { it.methods.asSequence() }
        .filter { method ->
            (method as? KtLightMethod)?.lightMemberOrigin?.isEquivalentTo(this) == true
        }.distinct()
}
