package com.linecorp.intellij.plugins.armeria.module

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the representative wizard matrix from the New Project Wizard review.
 */
class ArmeriaWizardTemplateRenderingTest {
    @Test
    fun gradleKtsKotlinServerGrpcJunit5() {
        val context =
            ArmeriaWizardTemplateTestContext(
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
        val context =
            ArmeriaWizardTemplateTestContext(
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
        val context =
            ArmeriaWizardTemplateTestContext(
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
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "kotlin",
                testRunnerId = "junit5",
                libraries = setOf("armeria-spring-boot3-starter"),
            )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.kts.ft", context)

        assertTrue(rendered.contains("armeria-spring-boot3-starter"))
        assertTrue(rendered.contains("org.springframework.boot"))
        assertTrue(rendered.contains("kotlin(\"plugin.spring\")"))
    }

    @Test
    fun springBoot3StarterRendersApplicationYmlAndConfigurator() {
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "kotlin",
                libraries = setOf("armeria-spring-boot3-starter"),
            )
        val applicationYml = renderBuildTemplate("fileTemplates/j2ee/armeria-application.yml.ft", context)
        val configurator =
            renderBuildTemplate("fileTemplates/j2ee/armeria-server-configurator.kt.ft", context)
        val main = renderBuildTemplate("fileTemplates/j2ee/armeria-main.kt.ft", context)

        assertTrue(applicationYml.contains("web-application-type: none"))
        assertTrue(applicationYml.contains("armeria:"))
        assertTrue(applicationYml.contains("port: 8080"))
        assertTrue(configurator.contains("ArmeriaServerConfigurator"))
        assertTrue(configurator.contains("open class ArmeriaConfiguration"))
        assertTrue(configurator.contains("armeriaServerConfigurator(blogService: BlogService)"))
        assertTrue(configurator.contains("annotatedService(blogService)"))
        assertTrue(main.contains("SpringApplication.run"))
        assertFalse(main.contains("Server.builder()"))
    }

    @Test
    fun grpcRendersProtoAndServiceStub() {
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "java",
                libraries = setOf("armeria-grpc"),
            )
        val proto = renderBuildTemplate("fileTemplates/j2ee/armeria-hello.proto.ft", context)
        val stub = renderBuildTemplate("fileTemplates/j2ee/armeria-grpc-service.java.ft", context)
        val main = renderBuildTemplate("fileTemplates/j2ee/armeria-main.java.ft", context)

        assertTrue(proto.contains("option java_package = \"${context.rootPackage}\";"))
        assertTrue(proto.contains("service HelloService"))
        assertTrue(stub.contains("class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase"))
        assertTrue(main.contains("GrpcService.builder()"))
        assertTrue(main.contains("new HelloServiceImpl()"))
    }

    @Test
    fun grpcBuildTemplateIncludesProtobufPlugin() {
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "java",
                libraries = setOf("armeria-grpc"),
            )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.kts.ft", context)

        assertTrue(rendered.contains("id(\"com.google.protobuf\")"))
        assertTrue(rendered.contains("protoc-gen-grpc-java"))
        assertTrue(rendered.contains("protobuf {"))
    }

    @Test
    fun grpcMavenTemplateLeavesOsClassifierPlaceholder() {
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "java",
                libraries = setOf("armeria-grpc"),
            )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-pom.xml.ft", context)

        assertTrue(rendered.contains("protobuf-maven-plugin"))
        assertTrue(rendered.contains("\${os.detected.classifier}"))
        assertTrue(rendered.contains("os-maven-plugin"))
    }

    @Test
    fun scalaLanguageWithoutArmeriaScalaLibraryStillHasStdlib() {
        val context = ArmeriaWizardTemplateTestContext(language = "scala")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.ft", context)

        assertTrue(rendered.contains("id 'scala'"))
        assertTrue(rendered.contains("scala-library:2.13.8"))
    }

    @Test
    fun webfluxStarterDoesNotDisableWebApplicationType() {
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "kotlin",
                libraries = setOf("armeria-spring-boot3-webflux-starter"),
            )
        val main = renderBuildTemplate("fileTemplates/j2ee/armeria-main.kt.ft", context)

        assertTrue(main.contains("Server.builder()"))
        assertFalse(main.contains("SpringApplication"))
    }

    @Test
    fun scalaMainTemplateRendersBlogService() {
        val context = ArmeriaWizardTemplateTestContext(language = "scala")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-main.scala.ft", context)

        assertTrue(rendered.contains("package ${context.rootPackage}"))
        assertTrue(rendered.contains("object Main"))
        assertTrue(rendered.contains("def main(args: Array[String]): Unit"))
        assertTrue(rendered.contains("new BlogService()"))
        assertTrue(rendered.contains("Server.builder()"))
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
    fun kotlinMainTemplateRendersBlogService() {
        val context = ArmeriaWizardTemplateTestContext(language = "kotlin")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-main.kt.ft", context)

        assertTrue(rendered.contains("package ${context.rootPackage}"))
        assertTrue(rendered.contains("fun main()"))
        assertTrue(rendered.contains("BlogService()"))
        assertTrue(rendered.contains("Server.builder()"))
        assertFalse(rendered.contains("SpringApplication"))
    }

    @Test
    fun javaBlogServiceTemplateRendersCrudRoutes() {
        val context = ArmeriaWizardTemplateTestContext(language = "java")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-blog-service.java.ft", context)

        assertTrue(rendered.contains("@Post(\"/blogs\")"))
        assertTrue(rendered.contains("@Get(\"/blogs/:id\")"))
        assertTrue(rendered.contains("@Get(\"/blogs\")"))
        assertTrue(rendered.contains("@Put(\"/blogs/:id\")"))
        assertTrue(rendered.contains("@Delete(\"/blogs/:id\")"))
        assertFalse(rendered.contains("@Component"))
    }

    @Test
    fun springBootBlogServiceUsesComponent() {
        val context =
            ArmeriaWizardTemplateTestContext(
                language = "kotlin",
                libraries = setOf("armeria-spring-boot3-starter"),
            )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-blog-service.kt.ft", context)

        assertTrue(rendered.contains("@Component"))
        assertTrue(rendered.contains("@Post(\"/blogs\")"))
    }

    @Test
    fun newCatalogLibrariesRenderWhenSelected() {
        val context =
            ArmeriaWizardTemplateTestContext(
                libraries =
                    setOf(
                        "armeria-consul",
                        "armeria-oauth2",
                        "armeria-reactor3",
                        "armeria-resilience4j2",
                        "armeria-resteasy",
                        "armeria-spring-boot3-actuator-starter",
                    ),
            )
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-build.gradle.kts.ft", context)

        assertTrue(rendered.contains("armeria-consul"))
        assertTrue(rendered.contains("armeria-oauth2"))
        assertTrue(rendered.contains("armeria-reactor3"))
        assertTrue(rendered.contains("armeria-resilience4j2"))
        assertTrue(rendered.contains("armeria-resteasy"))
        assertTrue(rendered.contains("armeria-spring-boot3-actuator-starter"))
    }

    @Test
    fun javaServiceTestTemplateRendersJUnit5Server() {
        val context = ArmeriaWizardTemplateTestContext(language = "java")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-service-test.java.ft", context)

        assertTrue(rendered.contains("package ${context.rootPackage};"))
        assertTrue(rendered.contains("com.linecorp.armeria.testing.junit5.server.ServerExtension"))
        assertTrue(rendered.contains("org.junit.jupiter.api.extension.RegisterExtension"))
        assertTrue(rendered.contains("@Get(\"/ping\")"))
        assertTrue(rendered.contains("void pingReturnsPong()"))
        assertTrue(rendered.contains("WebClient.of(server.httpUri())"))
    }

    @Test
    fun kotlinServiceTestTemplateRendersJUnit5Server() {
        val context = ArmeriaWizardTemplateTestContext(language = "kotlin")
        val rendered = renderBuildTemplate("fileTemplates/j2ee/armeria-service-test.kt.ft", context)

        assertTrue(rendered.contains("package ${context.rootPackage}"))
        assertTrue(rendered.contains("com.linecorp.armeria.testing.junit5.server.ServerExtension"))
        assertTrue(rendered.contains("org.junit.jupiter.api.extension.RegisterExtension"))
        assertTrue(rendered.contains("@Get(\"/ping\")"))
        assertTrue(rendered.contains("fun pingReturnsPong()"))
        assertTrue(rendered.contains("WebClient.of(server.httpUri())"))
    }

    @Test
    fun logbackTemplateRendersConsoleAppender() {
        val rendered =
            renderBuildTemplate(
                "fileTemplates/j2ee/armeria-logback.xml.ft",
                ArmeriaWizardTemplateTestContext(),
            )

        assertTrue(rendered.contains("ch.qos.logback.core.ConsoleAppender"))
        assertTrue(rendered.contains("<root level=\"INFO\">"))
    }

    private fun renderBuildTemplate(
        resourcePath: String,
        context: ArmeriaWizardTemplateTestContext,
    ): String = ArmeriaWizardTemplateRenderer.renderClasspathTemplate(resourcePath, context)
}
