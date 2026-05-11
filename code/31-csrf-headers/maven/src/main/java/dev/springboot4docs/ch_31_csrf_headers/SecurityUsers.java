package dev.springboot4docs.ch_31_csrf_headers;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

final class SecurityUsers {

	private SecurityUsers() {
	}

	static UserDetailsService demoUsers() {
		UserDetails alice = User.withUsername("alice")
				.password("{noop}secret")
				.roles("USER")
				.build();

		return new InMemoryUserDetailsManager(alice);
	}

}
