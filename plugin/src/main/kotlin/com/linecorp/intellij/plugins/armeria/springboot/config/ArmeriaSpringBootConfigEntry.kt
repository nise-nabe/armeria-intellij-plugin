package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

data class ArmeriaSpringBootConfigEntry(
    val key: String,
    val value: String,
    val navigationPointer: SmartPsiElementPointer<PsiElement>? = null,
    val configuratorFqn: String? = null,
)

data class ArmeriaSpringBootConfigFile(
    val fileName: String,
    val filePath: String,
    val entries: List<ArmeriaSpringBootConfigEntry>,
)
