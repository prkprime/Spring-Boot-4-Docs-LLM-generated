package dev.springboot4docs.ch_34_oauth2_login;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class WebController {

	@GetMapping("/")
	String home(@AuthenticationPrincipal OAuth2User user, Model model) {
		model.addAttribute("user", user);
		return "home";
	}

	@GetMapping("/login")
	String login() {
		return "login";
	}

	@GetMapping("/me")
	@ResponseBody
	Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
		if (user instanceof OidcUser oidcUser) {
			return Map.of(
					"type", "oidc",
					"name", oidcUser.getFullName(),
					"subject", oidcUser.getSubject(),
					"attributes", oidcUser.getAttributes(),
					"idTokenClaims", oidcUser.getIdToken().getClaims());
		}

		return Map.of(
				"type", "oauth2",
				"name", user.getName(),
				"attributes", user.getAttributes());
	}

}
