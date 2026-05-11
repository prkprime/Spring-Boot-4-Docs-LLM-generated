package dev.springboot4docs.ch_45_docker;

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
	void buildEndpointReportsRuntimeDetails() {
		this.restTestClient.get().uri("/api/build")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.application").isEqualTo("45-docker")
				.jsonPath("$.javaFeatureVersion").exists()
				.jsonPath("$.builtAt").isEqualTo("2026-05-10T00:00:00Z");
	}

	@Test
	void healthEndpointIsReadyForContainerProbes() {
		this.restTestClient.get().uri("/actuator/health/readiness")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

}
