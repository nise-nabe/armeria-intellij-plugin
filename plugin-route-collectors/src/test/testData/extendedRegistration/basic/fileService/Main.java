package example;

import com.linecorp.armeria.server.Server;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Server.builder()
            .fileService("/files/", new File("/tmp"))
            .build();
    }
}
