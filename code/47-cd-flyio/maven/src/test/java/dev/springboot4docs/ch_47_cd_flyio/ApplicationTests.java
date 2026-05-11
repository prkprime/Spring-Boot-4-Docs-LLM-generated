package dev.springboot4docs.ch_47_cd_flyio;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ApplicationTests {

	@Autowired
	private RestTestClient restTestClient;

	@Test
	void taskEndpointReturnsJson() {
		this.restTestClient.get().uri("/api/tasks")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$[0].title").isEqualTo("Build the image")
				.jsonPath("$[1].complete").isEqualTo(false);
	}

	@Test
	void readinessProbeIsAvailableForFlyMachines() {
		this.restTestClient.get().uri("/actuator/health/readiness")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

}
