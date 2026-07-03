package com.linecorp.intellij.plugins.armeria.module

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

object ArmeriaWizardTemplateRenderer {
    private val engine: VelocityEngine by lazy {
        val properties = Properties().apply {
            setProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT, "true")
        }
        VelocityEngine(properties).also { it.init() }
    }

    fun renderClasspathTemplate(resourcePath: String, context: ArmeriaWizardTemplateTestContext): String {
        val templateText = ArmeriaWizardTemplateRenderer::class.java.classLoader
            .getResourceAsStream(resourcePath)
            ?.readAllBytes()
            ?.toString(StandardCharsets.UTF_8)
            ?: error("Missing template resource: $resourcePath")

        val velocityContext = VelocityContext().apply {
            put("context", context)
            put("PACKAGE_NAME", context.group)
            // Maven POM templates also reference ${project.basedir} and ${kotlin.version}.
            put("project", mapOf("basedir" to "."))
            put(
                "kotlin",
                mapOf("version" to context.getVersion("org.jetbrains.kotlin", "kotlin-bom")),
            )
        }
        val writer = StringWriter()
        engine.evaluate(velocityContext, writer, resourcePath, templateText)
        return writer.toString()
    }
}
