package dev.springboot4docs.ch_03_configuration_basics;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(GreetingController.class)
@EnableConfigurationProperties(GreetingProperties.class)
@TestPropertySource(properties = {
        "greeting.prefix=Hello",
        "greeting.audience=Spring Boot 4",
        "greeting.punctuation=!"
})
class GreetingControllerTest {

    private final MockMvcTester mvc;

    @Autowired
    GreetingControllerTest(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void greetsConfiguredAudience() {
        mvc.get().uri("/greet")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .isEqualTo("Hello, Spring Boot 4!");
    }

}
