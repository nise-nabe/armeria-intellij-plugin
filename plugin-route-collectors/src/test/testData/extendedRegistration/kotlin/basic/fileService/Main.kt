package example

import com.linecorp.armeria.server.Server
import java.io.File

fun main() {
    Server.builder()
        .fileService("/files/", File("/tmp"))
        .build()
}
