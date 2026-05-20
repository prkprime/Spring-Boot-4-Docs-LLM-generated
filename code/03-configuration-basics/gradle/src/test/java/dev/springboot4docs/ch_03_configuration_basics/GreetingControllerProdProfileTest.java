package dev.springboot4docs.ch_03_configuration_basics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class GreetingControllerProdProfileTest {

    private final MockMvcTester mvc;

    @Autowired
    GreetingControllerProdProfileTest(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void prodProfileOverridesAudience() {
        mvc.get().uri("/greet")
                .assertThat()
                .hasStatusOk()
                .bodyText()
                .contains("Production");
    }

}
