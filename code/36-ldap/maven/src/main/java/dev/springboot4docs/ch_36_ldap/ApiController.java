package dev.springboot4docs.ch_36_ldap;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class ApiController {

	@GetMapping("/me")
	UserResponse me(Authentication authentication) {
		List<String> authorities = authentication.getAuthorities().stream()
				.map(Object::toString)
				.sorted()
				.toList();
		return new UserResponse(authentication.getName(), authorities);
	}

	@GetMapping("/admin/secrets")
	String adminSecrets() {
		return "admin secrets";
	}

	record UserResponse(String username, List<String> authorities) {
	}

}
