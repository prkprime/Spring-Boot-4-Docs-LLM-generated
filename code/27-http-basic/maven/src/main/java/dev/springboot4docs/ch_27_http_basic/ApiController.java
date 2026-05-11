package dev.springboot4docs.ch_27_http_basic;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiController {

	@GetMapping("/api/me")
	String me(Authentication authentication) {
		return authentication.getName();
	}

}
