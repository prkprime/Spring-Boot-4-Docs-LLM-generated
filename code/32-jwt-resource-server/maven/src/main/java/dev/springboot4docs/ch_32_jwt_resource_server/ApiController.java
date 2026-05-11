package dev.springboot4docs.ch_32_jwt_resource_server;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiController {

	@GetMapping("/public/info")
	String publicInfo() {
		return "public";
	}

	@GetMapping("/api/me")
	Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
		return Map.of(
				"subject", jwt.getSubject(),
				"scopes", scopes(jwt));
	}

	@GetMapping("/api/admin/secrets")
	String adminSecrets() {
		return "admin secrets";
	}

	private List<String> scopes(Jwt jwt) {
		String scope = jwt.getClaimAsString("scope");
		if (scope == null || scope.isBlank()) {
			return List.of();
		}
		return List.of(scope.split(" "));
	}

}
