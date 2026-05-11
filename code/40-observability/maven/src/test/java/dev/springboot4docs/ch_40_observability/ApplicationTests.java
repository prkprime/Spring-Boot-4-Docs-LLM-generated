package dev.springboot4docs.ch_40_observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@AutoConfigureMetrics
class ApplicationTests {

	@Autowired
	private RestTestClient restTestClient;

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	void prometheusEndpointIncludesCustomOrderCounter() {
		this.restTestClient.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new Order("book-100", 2))
				.exchange()
				.expectStatus().isCreated();

		this.restTestClient.get().uri("/actuator/prometheus")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.value((body) -> assertThat(body)
						.contains("orders_processed_total{")
						.contains("outcome=\"success\""));
	}

	@Test
	void postingOrderIncrementsCounterInMeterRegistry() {
		double before = successCounter();

		this.restTestClient.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new Order("notebook-200", 1))
				.exchange()
				.expectStatus().isCreated();

		assertThat(successCounter()).isEqualTo(before + 1.0);
	}

	@Test
	void metricsEndpointReportsCustomOrderCounterMeasurement() {
		this.restTestClient.post().uri("/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new Order("pencil-300", 5))
				.exchange()
				.expectStatus().isCreated();

		this.restTestClient.get().uri("/actuator/metrics/orders.processed.total")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("orders.processed.total")
				.jsonPath("$.measurements[0].value").exists();
	}

	private double successCounter() {
		return this.meterRegistry.counter("orders.processed.total", "outcome", "success").count();
	}

}
