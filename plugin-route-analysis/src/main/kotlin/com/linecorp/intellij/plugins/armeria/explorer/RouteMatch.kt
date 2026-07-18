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
    ROUTE_FLUENT,
    DECORATOR_UNDER,
    DELEGATED_SPRING_MVC,
    NON_HTTP,
    RUNTIME,
    /** Static application config (e.g. Spring Boot armeria.*) — HTTP-capable, not live DocService fetch. */
    CONFIG,
}
