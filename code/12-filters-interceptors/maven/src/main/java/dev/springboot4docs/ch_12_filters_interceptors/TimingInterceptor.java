package dev.springboot4docs.ch_12_filters_interceptors;

import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TimingInterceptor implements HandlerInterceptor {

	public static final String ELAPSED_HEADER = "X-Elapsed-Ms";

	private static final String START_TIME_ATTRIBUTE = TimingInterceptor.class.getName() + ".START_TIME";

	private static final Logger log = LoggerFactory.getLogger(TimingInterceptor.class);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
		if (handler instanceof HandlerMethod handlerMethod) {
			log.debug("Handling {} with {}", request.getRequestURI(), describe(handlerMethod));
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		Object start = request.getAttribute(START_TIME_ATTRIBUTE);
		if (start instanceof Long startTime) {
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
			response.setHeader(ELAPSED_HEADER, Long.toString(elapsedMs));
			log.info("{} {} handled by {} in {} ms", request.getMethod(), request.getRequestURI(), describe(handler),
					elapsedMs);
		}
	}

	private String describe(Object handler) {
		if (handler instanceof HandlerMethod handlerMethod) {
			return handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
		}
		return handler.toString();
	}

}
