package dev.springboot4docs.ch_12_filters_interceptors;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(EchoController.class)
@Import({ TimingInterceptor.class, WebConfig.class })
class EchoControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void apiEchoIncludesRequestIdFromFilterAndElapsedTimeFromInterceptor() {
		MvcTestResult result = this.mvc.get().uri("/api/echo?msg={message}", "hello")
				.exchange();

		assertThat(result).hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.bodyJson()
				.extractingPath("$.message").asString().isEqualTo("hello");

		assertThat(result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
		assertThat(result.getResponse().getHeader(TimingInterceptor.ELAPSED_HEADER)).isNotBlank();
	}

}
