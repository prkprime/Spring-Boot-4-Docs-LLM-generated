package dev.springboot4docs.ch_12_filters_interceptors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController {

	@GetMapping("/api/echo")
	EchoResponse echo(@RequestParam String msg, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return new EchoResponse(msg, requestId);
	}

	record EchoResponse(String message, String requestId) {
	}

}
