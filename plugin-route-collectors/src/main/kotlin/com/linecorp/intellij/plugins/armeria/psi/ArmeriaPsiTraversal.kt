package com.linecorp.intellij.plugins.armeria.psi

import com.intellij.psi.PsiElement

inline fun PsiElement.forEachDescendant(action: (PsiElement) -> Unit) {
    val queue = ArrayDeque<PsiElement>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val element = queue.removeFirst()
        action(element)
        for (child in element.children) {
            queue.add(child)
        }
    }
}
