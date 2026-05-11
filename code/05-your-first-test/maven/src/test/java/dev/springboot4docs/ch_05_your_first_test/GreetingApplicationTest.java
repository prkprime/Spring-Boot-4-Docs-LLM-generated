package dev.springboot4docs.ch_05_your_first_test;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class GreetingApplicationTest {

	@Autowired
	private RestTestClient restTestClient;

	@Test
	void greetsByNameOverHttp() {
		this.restTestClient.get().uri("/greet/{name}", "Spring")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.text").isEqualTo("Hello, Spring!");
	}

}
