package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaReferenceFileIndex
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaReferenceFileIndexTest {
    @Test
    fun referencesArmeriaPackage_includesWhitespaceSplitQualifiedNames() {
        assertTrue(ArmeriaReferenceFileIndex.referencesArmeriaPackage("import com.linecorp.armeria.client.WebClient;"))
        assertTrue(ArmeriaReferenceFileIndex.referencesArmeriaPackage("import com.linecorp.\n    armeria.client.WebClient;"))
    }

    @Test
    fun referencesArmeriaPackage_excludesUnrelatedPackages() {
        assertFalse(ArmeriaReferenceFileIndex.referencesArmeriaPackage("import example.armeria.client.WebClient;"))
    }
}
