package example

import com.linecorp.armeria.server.Server

fun main() {
    Server.builder()
        .healthCheckService()
        .build()
}
