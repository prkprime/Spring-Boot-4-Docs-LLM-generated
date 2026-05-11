package dev.springboot4docs.ch_29_jdbc_users;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiController {

	@GetMapping("/api/me")
	String me(Principal principal) {
		return principal.getName();
	}

}
