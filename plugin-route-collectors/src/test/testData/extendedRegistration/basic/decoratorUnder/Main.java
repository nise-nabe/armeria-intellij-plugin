package example;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.logging.LoggingService;

public class Main {
    public static void main(String[] args) {
        Server.builder()
            .decoratorUnder("/public", LoggingService.newDecorator())
            .build();
    }
}
