package dev.springboot4docs.ch_13_cors;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(WidgetController.class)
@Import(WebConfig.class)
class WidgetControllerWebMvcTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void preflightToGlobalEndpointFromAllowedOriginReturnsCorsHeaders() {
		MvcTestResult result = this.mvc.options().uri("/widgets/all")
				.header(HttpHeaders.ORIGIN, "https://app.example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
				.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.isEqualTo("https://app.example.com");
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
				.contains("GET");
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
				.contains("Content-Type");
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
				.isEqualTo("1800");
	}

	@Test
	void preflightToGlobalEndpointFromDisallowedOriginIsRejected() {
		MvcTestResult result = this.mvc.options().uri("/widgets/all")
				.header(HttpHeaders.ORIGIN, "https://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.exchange();

		assertThat(result.getResponse().getStatus() / 100).isNotEqualTo(2);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

	@Test
	void actualGetToGlobalEndpointFromAllowedOriginReturnsCorsHeader() {
		MvcTestResult result = this.mvc.get().uri("/widgets/all")
				.header(HttpHeaders.ORIGIN, "https://app.example.com")
				.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.isEqualTo("https://app.example.com");
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
				.contains("X-Total-Count");
	}

	@Test
	void actualGetToLocalEndpointFromAllowedOriginReturnsCorsHeader() {
		MvcTestResult result = this.mvc.get().uri("/widgets/local")
				.header(HttpHeaders.ORIGIN, "https://localhost:3000")
				.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.isEqualTo("https://localhost:3000");
	}

	@Test
	void actualGetToLocalEndpointFromDisallowedOriginIsRejected() {
		MvcTestResult result = this.mvc.get().uri("/widgets/local")
				.header(HttpHeaders.ORIGIN, "https://app.example.com")
				.exchange();

		assertThat(result.getResponse().getStatus() / 100).isNotEqualTo(2);
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

}
