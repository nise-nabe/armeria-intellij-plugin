package example;

import com.linecorp.armeria.server.Server;

public class Main {
    public static void main(String[] args) {
        Server.builder()
            .healthCheckService()
            .build();
    }
}
