package dev.springboot4docs.ch_15_async_sse_virtual_threads;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class EventsSpringBootTest {

	@Autowired
	private RestTestClient restTestClient;

	@Test
	void eventsEndpointStreamsTicks() {
		String body = assertTimeoutPreemptively(Duration.ofSeconds(8), () -> this.restTestClient.get().uri("/events")
				.accept(MediaType.TEXT_EVENT_STREAM)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
				.expectBody(String.class)
				.returnResult()
				.getResponseBody());

		assertThat(body).contains("event:tick");
		assertThat(body).contains("data:tick-1", "data:tick-2", "data:tick-3");
	}

}
