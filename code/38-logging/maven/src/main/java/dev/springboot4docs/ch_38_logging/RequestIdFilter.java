package dev.springboot4docs.ch_38_logging;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".REQUEST_ID";

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	public static final String TRACEPARENT_HEADER = "traceparent";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = requestId(request);
		String traceparent = request.getHeader(TRACEPARENT_HEADER);

		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		MDC.put("requestId", requestId);
		if (traceparent != null && !traceparent.isBlank()) {
			response.setHeader(TRACEPARENT_HEADER, traceparent);
			MDC.put(TRACEPARENT_HEADER, traceparent);
		}

		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(TRACEPARENT_HEADER);
			MDC.remove("requestId");
		}
	}

	private static String requestId(HttpServletRequest request) {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId != null && !requestId.isBlank()) {
			return requestId;
		}
		return UUID.randomUUID().toString();
	}

}
