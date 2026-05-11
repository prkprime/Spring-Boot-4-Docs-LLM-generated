package dev.springboot4docs.ch_28_form_login;

import java.security.Principal;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class WebController {

	@GetMapping("/")
	String home(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken)) {
			return "redirect:/dashboard";
		}
		return "home";
	}

	@GetMapping("/login")
	String login() {
		return "login";
	}

	@GetMapping("/dashboard")
	String dashboard(Principal principal, Model model) {
		model.addAttribute("username", principal.getName());
		return "dashboard";
	}

}
