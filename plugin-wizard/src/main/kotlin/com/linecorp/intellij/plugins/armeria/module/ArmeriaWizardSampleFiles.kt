package com.linecorp.intellij.plugins.armeria.module

internal data class ArmeriaWizardTemplateFile(
    val relativePath: String,
    val templateName: String,
)

internal object ArmeriaWizardSampleFiles {
    const val GRPC_LIBRARY_ID = "armeria-grpc"

    val SPRING_BOOT_LIBRARY_IDS: Set<String> =
        setOf(
            "armeria-spring-boot2-autoconfigure",
            "armeria-spring-boot3-autoconfigure",
            "armeria-spring-boot3-starter",
        )

    fun isSpringBoot(libraries: Set<String>): Boolean = libraries.any { it in SPRING_BOOT_LIBRARY_IDS }

    fun isGrpc(libraries: Set<String>): Boolean = GRPC_LIBRARY_ID in libraries

    fun sourceTemplates(
        languageId: String,
        libraries: Set<String>,
        junit5: Boolean,
        packagePath: String,
    ): List<ArmeriaWizardTemplateFile> {
        val languageDir =
            when (languageId) {
                "kotlin" -> "kotlin"
                "scala" -> "scala"
                else -> "java"
            }
        val extension =
            when (languageId) {
                "kotlin" -> "kt"
                "scala" -> "scala"
                else -> "java"
            }
        val sourceRoot = "src/main/$languageDir/$packagePath"
        val files = mutableListOf<ArmeriaWizardTemplateFile>()
        files += ArmeriaWizardTemplateFile("$sourceRoot/Main.$extension", "armeria-main.$extension")
        files += ArmeriaWizardTemplateFile("src/main/resources/logback.xml", "armeria-logback.xml")
        files += ArmeriaWizardTemplateFile("$sourceRoot/BlogService.$extension", "armeria-blog-service.$extension")
        if (isSpringBoot(libraries)) {
            files += ArmeriaWizardTemplateFile("src/main/resources/application.yml", "armeria-application.yml")
            files +=
                ArmeriaWizardTemplateFile(
                    "$sourceRoot/ArmeriaConfiguration.$extension",
                    "armeria-server-configurator.$extension",
                )
        }
        if (isGrpc(libraries)) {
            files += ArmeriaWizardTemplateFile("src/main/proto/hello.proto", "armeria-hello.proto")
            files +=
                ArmeriaWizardTemplateFile(
                    "$sourceRoot/HelloServiceImpl.$extension",
                    "armeria-grpc-service.$extension",
                )
        }
        if (junit5 && languageId != "scala") {
            files +=
                ArmeriaWizardTemplateFile(
                    "src/test/$languageDir/$packagePath/MainTest.$extension",
                    "armeria-service-test.$extension",
                )
        }
        return files
    }
}
