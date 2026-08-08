package example

import com.linecorp.armeria.server.RouteBuilder
import com.linecorp.armeria.server.Server

fun main() {
    Server.builder()
        .withRoute { _: RouteBuilder -> null }
        .withRoute { route: RouteBuilder ->
            route.post("/wrapped").build(Any())
        }
        .build()
}
