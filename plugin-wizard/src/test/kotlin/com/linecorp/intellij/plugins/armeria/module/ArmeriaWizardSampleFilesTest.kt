package com.linecorp.intellij.plugins.armeria.module

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaWizardSampleFilesTest {
    @Test
    fun springBoot3StarterKotlinGeneratesConfiguratorAndApplicationYml() {
        val files =
            ArmeriaWizardSampleFiles.sourceTemplates(
                languageId = "kotlin",
                libraries = setOf("armeria-spring-boot3-starter"),
                junit5 = true,
                packagePath = "com/example/demo",
            )
        val paths = files.map { it.relativePath }

        assertTrue(paths.contains("src/main/resources/application.yml"))
        assertTrue(paths.contains("src/main/kotlin/com/example/demo/ArmeriaConfiguration.kt"))
        assertTrue(paths.contains("src/main/kotlin/com/example/demo/Main.kt"))
        assertTrue(paths.contains("src/main/kotlin/com/example/demo/BlogService.kt"))
        assertFalse(paths.any { it.endsWith("hello.proto") })
    }

    @Test
    fun grpcJavaGeneratesProtoAndServiceStub() {
        val files =
            ArmeriaWizardSampleFiles.sourceTemplates(
                languageId = "java",
                libraries = setOf("armeria-grpc"),
                junit5 = true,
                packagePath = "com/example/demo",
            )
        val paths = files.map { it.relativePath }

        assertTrue(paths.contains("src/main/proto/hello.proto"))
        assertTrue(paths.contains("src/main/java/com/example/demo/HelloServiceImpl.java"))
        assertTrue(paths.contains("src/main/java/com/example/demo/Main.java"))
        assertFalse(paths.any { it.contains("application.yml") })
        assertFalse(paths.any { it.contains("ArmeriaConfiguration") })
    }

    @Test
    fun scalaCoreArmeriaGeneratesMainAndBlogService() {
        val files =
            ArmeriaWizardSampleFiles.sourceTemplates(
                languageId = "scala",
                libraries = emptySet(),
                junit5 = true,
                packagePath = "com/example/demo",
            )
        val paths = files.map { it.relativePath }

        assertTrue(paths.contains("src/main/scala/com/example/demo/Main.scala"))
        assertTrue(paths.contains("src/main/scala/com/example/demo/BlogService.scala"))
        assertTrue(paths.contains("src/main/resources/logback.xml"))
        assertFalse(paths.any { it.contains("MainTest") })
    }

    @Test
    fun webfluxStarterDoesNotGenerateArmeriaServerSample() {
        val files =
            ArmeriaWizardSampleFiles.sourceTemplates(
                languageId = "kotlin",
                libraries = setOf("armeria-spring-boot3-webflux-starter"),
                junit5 = true,
                packagePath = "com/example/demo",
            )
        val paths = files.map { it.relativePath }

        assertFalse(paths.any { it.contains("application.yml") })
        assertFalse(paths.any { it.contains("ArmeriaConfiguration") })
        assertTrue(paths.contains("src/main/kotlin/com/example/demo/Main.kt"))
    }

    @Test
    fun defaultJavaRestSampleOmitsSpringAndGrpcFiles() {
        val files =
            ArmeriaWizardSampleFiles.sourceTemplates(
                languageId = "java",
                libraries = emptySet(),
                junit5 = false,
                packagePath = "com/example/demo",
            )
        val paths = files.map { it.relativePath }

        assertTrue(paths.contains("src/main/java/com/example/demo/Main.java"))
        assertTrue(paths.contains("src/main/java/com/example/demo/BlogService.java"))
        assertFalse(paths.any { it.contains("MainTest") })
        assertFalse(paths.any { it.contains("hello.proto") })
        assertFalse(paths.any { it.contains("application.yml") })
    }
}
