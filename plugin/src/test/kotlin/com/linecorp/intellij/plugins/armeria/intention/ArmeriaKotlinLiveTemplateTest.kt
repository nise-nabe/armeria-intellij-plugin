package com.linecorp.intellij.plugins.armeria.intention

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaKotlinLiveTemplateTest {
    @Test
    fun restClientExecuteTemplateUsesKotlinExtensionExecute() {
        val xml = liveTemplateXml()
        assertTrue(xml.contains("name=\"armrcex-kotlin\""), xml)
        assertTrue(xml.contains("RestClient.of"), xml)
        assertTrue(xml.contains("com.linecorp.armeria.client.kotlin.execute"), xml)
        assertFalse(xml.contains("runBlocking"), xml)
        assertTrue(xml.contains("live.template.restclient.execute.kotlin.description"), xml)
    }

    @Test
    fun coroutineContextServiceTemplateBuildsAnnotatedService() {
        val xml = liveTemplateXml()
        assertTrue(xml.contains("name=\"armccs-kotlin\""), xml)
        assertTrue(xml.contains("Server.builder()"), xml)
        assertTrue(xml.contains("CoroutineContextService.newDecorator"), xml)
        assertTrue(xml.contains(".annotatedService()"), xml)
        assertTrue(xml.contains("live.template.coroutine.context.service.kotlin.description"), xml)
    }

    private fun liveTemplateXml(): String {
        val url =
            javaClass.getResource("/liveTemplates/ArmeriaKotlin.xml")
                ?: error("ArmeriaKotlin.xml is missing from the plugin classpath")
        return url.readText()
    }
}
