package com.linecorp.intellij.plugins.armeria.explorer

enum class RouteMatch {
    ANNOTATED_HTTP,
    ANNOTATED_SERVICE,
    SERVICE,
    SERVICE_UNDER,
    FILE_SERVICE,
    HEALTH_CHECK,
    VIRTUAL_HOST,
    ROUTE_DECORATOR,
    NON_HTTP,
    RUNTIME,
}
