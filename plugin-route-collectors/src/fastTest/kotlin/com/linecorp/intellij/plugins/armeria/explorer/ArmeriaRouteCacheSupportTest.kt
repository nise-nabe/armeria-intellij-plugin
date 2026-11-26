package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCacheSupport
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaRouteCacheSupportTest {
    @Test
    fun isRoutePsiLanguage_includesRouteSourceAndConfigLanguages() {
        for (id in listOf("JAVA", "kotlin", "Scala", "yaml", "Properties", "protobuf", "GraphQL", "thrift", "TEXT")) {
            assertTrue(ArmeriaRouteCacheSupport.isRoutePsiLanguageId(id), id)
        }
    }

    @Test
    fun isRoutePsiLanguage_excludesUnrelatedLanguages() {
        assertFalse(ArmeriaRouteCacheSupport.isRoutePsiLanguageId("XML"))
        assertFalse(ArmeriaRouteCacheSupport.isRoutePsiLanguageId("Markdown"))
    }
}
