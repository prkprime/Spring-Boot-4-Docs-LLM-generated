package dev.springboot4docs.ch_38_logging;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class LoggerEndpointIntegrationTest {

	@Autowired
	private RestTestClient restTestClient;

	@Test
	void changesLoggerLevelAtRuntime() {
		this.restTestClient.post().uri("/actuator/loggers/dev.springboot4docs")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"configuredLevel":"WARN"}
						""")
				.exchange()
				.expectStatus().isNoContent();

		this.restTestClient.get().uri("/actuator/loggers/dev.springboot4docs")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.configuredLevel").isEqualTo("WARN")
				.jsonPath("$.effectiveLevel").isEqualTo("WARN");
	}

}
