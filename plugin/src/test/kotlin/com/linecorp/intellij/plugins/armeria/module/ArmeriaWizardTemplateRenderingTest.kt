package com.linecorp.intellij.plugins.armeria.module

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the representative wizard matrix from the New Project Wizard review.
 */
class ArmeriaWizardTemplateRenderingTest {
    @Test
    fun gradleKtsKotlinServerGrpcJunit5() {
        val context = ArmeriaWizardTemplateTestContext(
            language = "kotlin",
            testRunnerId = "junit5",
            libraries = setOf("armeria-grpc"),
        )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.kts.ft", context)

        assertTrue(rendered.contains("kotlin(\"jvm\")"))
        assertTrue(rendered.contains("armeria-grpc"))
        assertTrue(rendered.contains("armeria-junit5"))
        assertTrue(rendered.contains("useJUnitJupiter()"))
        assertFalse(rendered.contains("armeria-tomcat8"))
    }

    @Test
    fun gradleGroovyScalaArmeriaScala213() {
        val context = ArmeriaWizardTemplateTestContext(
            language = "scala",
            testRunnerId = "junit5",
            libraries = setOf("armeria-scala_2.13"),
        )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.ft", context)

        assertTrue(rendered.contains("id 'scala'"))
        assertTrue(rendered.contains("armeria-scala_2.13"))
        assertTrue(rendered.contains("scala-library:2.13.8"))
    }

    @Test
    fun mavenKotlinTomcat8() {
        val context = ArmeriaWizardTemplateTestContext(
            language = "kotlin",
            testRunnerId = "junit5",
            libraries = setOf("armeria-tomcat8"),
        )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-pom.xml.ft", context)

        assertTrue(rendered.contains("<artifactId>armeria-tomcat8</artifactId>"))
        assertTrue(rendered.contains("kotlin-maven-plugin"))
        assertFalse(rendered.contains("<artifactId>armeria-tomcat9</artifactId>"))
    }

    @Test
    fun gradleKtsSpringBoot3Starter() {
        val context = ArmeriaWizardTemplateTestContext(
            language = "kotlin",
            testRunnerId = "junit5",
            libraries = setOf("armeria-spring-boot3-starter"),
        )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.kts.ft", context)

        assertTrue(rendered.contains("armeria-spring-boot3-starter"))
    }

    @Test
    fun libraryBlocksAreOmittedWhenNotSelected() {
        val context = ArmeriaWizardTemplateTestContext(libraries = emptySet())
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.kts.ft", context)

        assertFalse(rendered.contains("armeria-grpc"))
        assertFalse(rendered.contains("armeria-tomcat8"))
        assertTrue(rendered.contains("""implementation("com.linecorp.armeria:armeria")"""))
    }

    @Test
    fun kotlinMainTemplateRendersAnnotatedService() {
        val context = ArmeriaWizardTemplateTestContext(language = "kotlin", group = "com.example.demo")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-main.kt.ft", context)

        assertTrue(rendered.contains("package com.example.demo"))
        assertTrue(rendered.contains("@Get(\"/hello\")"))
        assertTrue(rendered.contains("fun main()"))
    }

    @Test
    fun javaServiceTestTemplateRendersJUnit5Server() {
        val context = ArmeriaWizardTemplateTestContext(language = "java", group = "com.example.demo")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-service-test.java.ft", context)

        assertTrue(rendered.contains("package com.example.demo;"))
        assertTrue(rendered.contains("org.junit.jupiter.api.Test"))
        assertTrue(rendered.contains("@Get(\"/ping\")"))
        assertTrue(rendered.contains(".http(0)"))
    }

    @Test
    fun kotlinServiceTestTemplateRendersJUnit5Server() {
        val context = ArmeriaWizardTemplateTestContext(language = "kotlin", group = "com.example.demo")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-service-test.kt.ft", context)

        assertTrue(rendered.contains("package com.example.demo"))
        assertTrue(rendered.contains("org.junit.jupiter.api.Test"))
        assertTrue(rendered.contains("@Get(\"/ping\")"))
        assertTrue(rendered.contains(".http(0)"))
    }

    @Test
    fun logbackTemplateRendersConsoleAppender() {
        val rendered = renderBuildTemplate(
            "fileTemplates/j2ee/armeria-logback.xml.ft",
            ArmeriaWizardTemplateTestContext(),
        )

        assertTrue(rendered.contains("ch.qos.logback.core.ConsoleAppender"))
        assertTrue(rendered.contains("<root level=\"INFO\">"))
    }

    private fun renderBuildTemplate(resourcePath: String, context: ArmeriaWizardTemplateTestContext): String =
        ArmeriaWizardTemplateRenderer.renderClasspathTemplate(resourcePath, context)
}
