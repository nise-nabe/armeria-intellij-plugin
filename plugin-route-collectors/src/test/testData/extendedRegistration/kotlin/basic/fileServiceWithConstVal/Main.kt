package example

import com.linecorp.armeria.server.Server
import java.io.File

private const val FILES_PATH = "/files/"

fun main() {
    Server.builder()
        .fileService(FILES_PATH, File("/tmp"))
        .build()
}
