package dev.springboot4docs.ch_02_dev_loop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(TimeController.class)
class TimeControllerTest {

    private final MockMvcTester mvc;

    @Autowired
    TimeControllerTest(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void nowReturnsJsonWithNowField() {
        mvc.get().uri("/now")
                .assertThat()
                .hasStatusOk()
                .bodyJson().hasPath("$.now");
    }

}
