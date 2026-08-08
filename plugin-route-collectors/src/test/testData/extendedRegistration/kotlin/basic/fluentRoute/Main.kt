package example

import com.linecorp.armeria.server.Server

fun main() {
    Server.builder()
        .route()
        .post("/api/items")
        .build(Any())
        .build()
}
