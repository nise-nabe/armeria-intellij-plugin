package example;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.file.FileService;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Server.builder()
            .fileService("/static/", FileService.of(new File("src/main/resources/public")))
            .build();
    }
}
