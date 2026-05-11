package dev.springboot4docs.ch_31_csrf_headers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("default")
class BrowserAppSecurityConfig {

	@Bean
	SecurityFilterChain browserAppSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
				.formLogin(Customizer.withDefaults())
				.build();
	}

	@Bean
	UserDetailsService browserAppUsers() {
		return SecurityUsers.demoUsers();
	}

}
