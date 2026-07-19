package com.linecorp.intellij.plugins.armeria.explorer.model

internal enum class CoreServiceRegistrationMethod(
    val methodName: String,
) {
    SERVICE("service"),
    SERVICE_UNDER("serviceUnder"),
    ANNOTATED_SERVICE("annotatedService"),
    ;

    companion object {
        val METHOD_NAMES: Set<String> = entries.mapTo(linkedSetOf()) { it.methodName }

        fun fromMethodName(name: String): CoreServiceRegistrationMethod? = entries.firstOrNull { it.methodName == name }
    }
}
