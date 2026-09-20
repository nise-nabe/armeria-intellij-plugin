package example

import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.file.FileService
import java.io.File

fun main() {
    Server.builder()
        .fileService("/static/", FileService.of(File("src/main/resources/public")))
        .build()
}
