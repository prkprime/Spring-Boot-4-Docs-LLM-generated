package dev.springboot4docs.ch_15_async_sse_virtual_threads;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(JobsController.class)
class JobsControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void jobEndpointCompletesAsynchronously() throws Exception {
		MvcTestResult result = this.mvc.get().uri("/jobs/{id}", 42)
				.exchange(Duration.ofSeconds(2));

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getContentAsString()).isEqualTo("""
				{"id":42,"status":"complete"}""");
	}

}
