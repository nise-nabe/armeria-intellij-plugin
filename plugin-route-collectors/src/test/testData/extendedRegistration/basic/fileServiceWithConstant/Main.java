package example;

import com.linecorp.armeria.server.Server;
import java.io.File;

public class Main {
    private static final String FILES_PATH = "/files/";

    public static void main(String[] args) {
        Server.builder()
            .fileService(FILES_PATH, new File("/tmp"))
            .build();
    }
}
