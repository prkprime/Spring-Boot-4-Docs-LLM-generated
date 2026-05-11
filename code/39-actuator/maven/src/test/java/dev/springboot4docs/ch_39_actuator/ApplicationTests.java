package dev.springboot4docs.ch_39_actuator;

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
	void healthEndpointReportsUp() {
		this.restTestClient.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void infoEndpointIncludesAppAndBuildSections() {
		this.restTestClient.get().uri("/actuator/info")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.app.name").isEqualTo("39-actuator")
				.jsonPath("$.app.version").isEqualTo("0.0.1")
				.jsonPath("$.build.artifact").isEqualTo("39-actuator")
				.jsonPath("$.build.java").isEqualTo("25");
	}

	@Test
	void kubernetesHealthProbesAreAvailable() {
		this.restTestClient.get().uri("/actuator/health/liveness")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");

		this.restTestClient.get().uri("/actuator/health/readiness")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	@Test
	void rootLoggerEndpointReportsConfiguredLevel() {
		this.restTestClient.get().uri("/actuator/loggers/ROOT")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.configuredLevel").exists();
	}

	@Test
	void metricsEndpointListsNamesAndJvmMemoryMeasurement() {
		this.restTestClient.get().uri("/actuator/metrics")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.names").isArray()
				.jsonPath("$.names[?(@ == 'jvm.memory.used')]").exists();

		this.restTestClient.get().uri("/actuator/metrics/jvm.memory.used")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.measurements[0].value").exists();
	}

}
