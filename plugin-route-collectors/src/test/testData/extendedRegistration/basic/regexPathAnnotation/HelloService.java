package example;

import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Path;

public class HelloService {
    @Get
    @Path("regex: /foo")
    public String hello() {
        return "hello";
    }
}
