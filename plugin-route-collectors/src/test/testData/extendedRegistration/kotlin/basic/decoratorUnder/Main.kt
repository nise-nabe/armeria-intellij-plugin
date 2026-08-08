package example

import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.logging.LoggingService

fun main() {
    Server.builder()
        .decoratorUnder("/public", LoggingService.newDecorator())
        .build()
}
