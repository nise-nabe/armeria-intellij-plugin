package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement

/**
 * Detects the `armeria-kotlin` module on a classpath by resolving a published API type,
 * with the internal [ARMERIA_KOTLIN_UTIL] marker as a fallback (issue #406).
 */
internal object ArmeriaKotlinClasspath {
    const val COROUTINE_CONTEXT_SERVICE = "com.linecorp.armeria.server.kotlin.CoroutineContextService"
    const val ARMERIA_KOTLIN_UTIL = "com.linecorp.armeria.internal.common.kotlin.ArmeriaKotlinUtil"

    private val MARKER_CLASSES =
        listOf(
            COROUTINE_CONTEXT_SERVICE,
            ARMERIA_KOTLIN_UTIL,
        )

    fun isPresent(element: PsiElement): Boolean {
        val facade = JavaPsiFacade.getInstance(element.project)
        return try {
            MARKER_CLASSES.any { facade.findClass(it, element.resolveScope) != null }
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: IndexNotReadyException) {
            true
        }
    }
}
