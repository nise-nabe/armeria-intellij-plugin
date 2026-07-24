package example;

import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Path;

public class HelloService {
    @Get
    @Path("prefix:/hello")
    public String hello() {
        return "hello";
    }
}
