package dev.springboot4docs.ch_35_oauth2_keycloak;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiController {

	@GetMapping("/api/me")
	Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
		return Map.of(
				"subject", jwt.getSubject(),
				"username", jwt.getClaimAsString("preferred_username"),
				"roles", realmRoles(jwt));
	}

	@GetMapping("/api/admin/secrets")
	String adminSecrets() {
		return "admin secrets";
	}

	@SuppressWarnings("unchecked")
	private List<String> realmRoles(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaim("realm_access");
		if (realmAccess == null) {
			return List.of();
		}
		Object roles = realmAccess.get("roles");
		if (roles instanceof List<?> roleList) {
			return (List<String>) roleList;
		}
		return List.of();
	}

}
