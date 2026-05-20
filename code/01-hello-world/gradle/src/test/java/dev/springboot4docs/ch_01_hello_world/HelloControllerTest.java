package dev.springboot4docs.ch_01_hello_world;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    private final MockMvcTester mvc;

    @Autowired
    HelloControllerTest(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void helloReturnsGreeting() {
        mvc.get().uri("/hello")
                .assertThat()
                .hasStatusOk()
                .bodyText().contains("Hello, Spring Boot 4!");
    }

}
