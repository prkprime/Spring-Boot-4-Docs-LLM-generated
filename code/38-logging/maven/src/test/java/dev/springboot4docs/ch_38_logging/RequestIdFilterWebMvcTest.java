package dev.springboot4docs.ch_38_logging;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(OrderController.class)
@Import({ RequestIdFilter.class, RequestIdFilterWebMvcTest.MdcProbeConfig.class })
class RequestIdFilterWebMvcTest {

	private static final String REQUEST_ID = "request-123";

	private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private LoggingDemoService loggingDemoService;

	@Test
	void requestIdFilterSetsMdcAndResponseHeaders() {
		MdcProbeConfig.requestIdSeen.set(null);
		MdcProbeConfig.traceparentSeen.set(null);

		MvcTestResult result = this.mvc.get().uri("/orders/{id}", 101)
				.header(RequestIdFilter.REQUEST_ID_HEADER, REQUEST_ID)
				.header(RequestIdFilter.TRACEPARENT_HEADER, TRACEPARENT)
				.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(REQUEST_ID);
		assertThat(result.getResponse().getHeader(RequestIdFilter.TRACEPARENT_HEADER)).isEqualTo(TRACEPARENT);
		assertThat(MdcProbeConfig.requestIdSeen.get()).isEqualTo(REQUEST_ID);
		assertThat(MdcProbeConfig.traceparentSeen.get()).isEqualTo(TRACEPARENT);
		assertThat(MDC.get("requestId")).isNull();
	}

	static class MdcProbeConfig {

		private static final AtomicReference<String> requestIdSeen = new AtomicReference<>();

		private static final AtomicReference<String> traceparentSeen = new AtomicReference<>();

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE + 1)
		Filter mdcProbeFilter() {
			return new Filter() {
				@Override
				public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
						throws IOException, ServletException {
					requestIdSeen.set(MDC.get("requestId"));
					traceparentSeen.set(MDC.get(RequestIdFilter.TRACEPARENT_HEADER));
					chain.doFilter(request, response);
				}
			};
		}

	}

}
