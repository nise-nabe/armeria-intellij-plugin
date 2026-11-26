package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtClassBody
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
    val declaration = (originalElement as? KtNamedFunction) ?: this
    val lightClasses =
        buildList {
            when (val parent = declaration.parent) {
                is KtFile -> {
                    parent.findArmeriaFacadeClass()?.let(::add)
                }

                is KtClassBody -> {
                    val containingClass = parent.parent as? KtClassOrObject
                    containingClass?.toArmeriaLightClass()?.let(::add)
                    if (containingClass is KtObjectDeclaration && containingClass.isCompanion()) {
                        PsiTreeUtil
                            .getParentOfType(containingClass, KtClassOrObject::class.java)
                            ?.toArmeriaLightClass()
                            ?.let(::add)
                    }
                }
            }
        }
    return lightClasses
        .flatMap { it.methods.asSequence() }
        .filter { method ->
            (method as? KtLightMethod)?.kotlinOrigin == declaration
        }
}
