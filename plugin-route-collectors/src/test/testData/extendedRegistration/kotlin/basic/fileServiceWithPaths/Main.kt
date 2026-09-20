package example

import com.linecorp.armeria.server.Server
import java.nio.file.Paths

fun main() {
    Server.builder()
        .fileService("/static/", Paths.get("src/main/resources", "static"))
        .build()
}
