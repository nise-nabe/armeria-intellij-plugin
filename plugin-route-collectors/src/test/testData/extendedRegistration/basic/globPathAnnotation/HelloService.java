package example;

import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Path;

public class HelloService {
    @Get
    @Path("glob:foo/**")
    public String hello() {
        return "hello";
    }
}
