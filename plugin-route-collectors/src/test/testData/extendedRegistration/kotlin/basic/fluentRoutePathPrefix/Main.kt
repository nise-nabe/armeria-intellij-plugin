package example

import com.linecorp.armeria.server.Server

fun main() {
    Server.builder()
        .route()
        .pathPrefix("/api")
        .get("/items")
        .build(Any())
        .build()
}
