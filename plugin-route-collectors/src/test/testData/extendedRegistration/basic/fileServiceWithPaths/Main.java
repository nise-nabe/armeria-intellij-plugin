package example;

import com.linecorp.armeria.server.Server;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Server.builder()
            .fileService("/static/", Paths.get("src/main/resources", "static"))
            .build();
    }
}
