package com.linecorp.intellij.plugins.armeria.explorer

internal object ArmeriaRouteContentScan {
    private const val FQCN_SERVER_BUILDER_CALL_PATTERN =
        """(?<![\w"])com\.linecorp\.armeria\.server\.Server\.builder\(\)"""
    private const val UNQUALIFIED_SERVER_BUILDER_CALL_PATTERN =
        """(?<![.\w"])Server\.builder\(\)"""

    private val ARMERIA_REFERENCE_PATTERN =
        Regex("""(?<![\w"])com\.linecorp\.armeria(?:\.[A-Za-z_][A-Za-z0-9_]*)+""")
    private val SERVER_BUILDER_IDENTIFIER = Regex("""(?<![\w"])serverBuilder(?![\w"])""")
    private val FQCN_SERVER_BUILDER_CALL = Regex(FQCN_SERVER_BUILDER_CALL_PATTERN)
    private val UNQUALIFIED_SERVER_BUILDER_CALL = Regex(UNQUALIFIED_SERVER_BUILDER_CALL_PATTERN)
    private val SERVER_BUILDER_CALL =
        Regex("""(?:$FQCN_SERVER_BUILDER_CALL_PATTERN|$UNQUALIFIED_SERVER_BUILDER_CALL_PATTERN)""")
    private val ROUTE_DECORATOR_CALL = Regex("""(?<![\w"])routeDecorator\s*\(""")

    fun referencesArmeriaInText(contents: CharSequence, scanLimit: Int = ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT): Boolean {
        val searchWindow = contents.subSequence(0, minOf(contents.length, scanLimit))
        return ARMERIA_REFERENCE_PATTERN.containsMatchIn(searchWindow)
    }

    fun referencesArmeriaKotlinContentInText(contents: CharSequence): Boolean {
        val header = contents.subSequence(0, minOf(contents.length, ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT))
        if (header.contains("import ${ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX}")) {
            return true
        }
        return referencesArmeriaInText(contents)
    }

    fun mayReferenceSpringBootArmeriaInText(contents: CharSequence): Boolean {
        if (referencesArmeriaKotlinContentInText(contents)) {
            return true
        }
        val header = contents.subSequence(0, minOf(contents.length, ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT))
        return ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any { indicator ->
            header.contains(indicator)
        }
    }

    fun looksLikeServerBuilderReceiverText(text: String): Boolean {
        return SERVER_BUILDER_CALL.containsMatchIn(text) || SERVER_BUILDER_IDENTIFIER.containsMatchIn(text)
    }

    fun looksLikeRouteDecoratorReceiverText(text: String): Boolean {
        return ROUTE_DECORATOR_CALL.containsMatchIn(text)
    }

    fun referencesArmeriaApplicationInSource(contents: CharSequence): Boolean {
        if (FQCN_SERVER_BUILDER_CALL.containsMatchIn(contents)) {
            return true
        }
        return referencesArmeriaInText(contents) &&
            UNQUALIFIED_SERVER_BUILDER_CALL.containsMatchIn(contents)
    }
}
