package dev.springboot4docs.ch_05_your_first_test;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.Mockito.when;

@WebMvcTest(GreetingController.class)
class GreetingControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private GreetingService greetingService;

	@Test
	void greetsByName() {
		when(this.greetingService.greet("Spring")).thenReturn(new Greeting("Hello, Spring!"));

		this.mvc.get().uri("/greet/{name}", "Spring")
				.assertThat()
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.text")
				.isEqualTo("Hello, Spring!");
	}

}
