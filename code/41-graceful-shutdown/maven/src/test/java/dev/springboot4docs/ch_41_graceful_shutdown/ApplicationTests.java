package dev.springboot4docs.ch_41_graceful_shutdown;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ApplicationTests {

	@Autowired
	private Environment environment;

	@Autowired
	private RestTestClient restTestClient;

	@Autowired
	private ShutdownPhaseLogger shutdownPhaseLogger;

	@Test
	void gracefulShutdownIsEnabled() {
		assertThat(this.environment.getProperty("server.shutdown")).isEqualTo("graceful");
	}

	@Test
	void shutdownTimeoutIsConfigured() {
		assertThat(this.environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
				.isEqualTo("25s");
	}

	@Test
	void shutdownPhaseLoggerIsRegistered() {
		assertThat(this.shutdownPhaseLogger).isNotNull();
		assertThat(this.shutdownPhaseLogger.isRunning()).isTrue();
	}

}
