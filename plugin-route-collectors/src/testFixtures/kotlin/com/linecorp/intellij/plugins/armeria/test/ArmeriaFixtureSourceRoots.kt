package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

fun JavaCodeInsightTestFixture.withTemporaryMainSourceRoot(block: (VirtualFile) -> Unit) {
    val mainRoot = tempDirFixture.findOrCreateDir("main")
    try {
        PsiTestUtil.addSourceRoot(module, mainRoot, false)
        block(mainRoot)
    } finally {
        PsiTestUtil.removeSourceRoot(module, mainRoot)
    }
}

fun JavaCodeInsightTestFixture.withTemporaryTestSourceRoot(block: (VirtualFile) -> Unit) {
    val testRoot = tempDirFixture.findOrCreateDir("test-sources")
    try {
        PsiTestUtil.addSourceRoot(module, testRoot, true)
        block(testRoot)
    } finally {
        PsiTestUtil.removeSourceRoot(module, testRoot)
    }
}
