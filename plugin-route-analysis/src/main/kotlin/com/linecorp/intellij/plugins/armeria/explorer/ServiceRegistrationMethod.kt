package com.linecorp.intellij.plugins.armeria.explorer

enum class ServiceRegistrationMethod(
    val methodName: String,
) {
    SERVICE("service"),
    SERVICE_UNDER("serviceUnder"),
    ANNOTATED_SERVICE("annotatedService"),
    FILE_SERVICE("fileService"),
    HEALTH_CHECK_SERVICE("healthCheckService"),
    VIRTUAL_HOST("virtualHost"),
    ROUTE_DECORATOR("routeDecorator"),
    ROUTE("route"),
    WITH_ROUTE("withRoute"),
    DECORATOR_UNDER("decoratorUnder"),
    ;

    companion object {
        val METHOD_NAMES: Set<String> = entries.mapTo(linkedSetOf()) { it.methodName }
        val EXTENDED_METHOD_NAMES: Set<String> =
            setOf(
                FILE_SERVICE.methodName,
                HEALTH_CHECK_SERVICE.methodName,
                VIRTUAL_HOST.methodName,
                ROUTE_DECORATOR.methodName,
                WITH_ROUTE.methodName,
                DECORATOR_UNDER.methodName,
            )
        val CORE_METHOD_NAMES: Set<String> = CoreServiceRegistrationMethod.METHOD_NAMES
        val FLUENT_ROUTE_HTTP_METHODS: Set<String> =
            setOf(
                "get",
                "head",
                "post",
                "put",
                "delete",
                "options",
                "patch",
                "trace",
            )

        fun fromMethodName(name: String): ServiceRegistrationMethod? = entries.firstOrNull { it.methodName == name }
    }
}
