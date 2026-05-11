package dev.springboot4docs.ch_31_csrf_headers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("api")
class ApiOnlySecurityConfig {

	@Bean
	SecurityFilterChain apiOnlySecurityFilterChain(HttpSecurity http) throws Exception {
		return http
				// A stateless API that authenticates each request with a header has no ambient cookie
				// credential for a forged browser form to replay, so CSRF is intentionally disabled.
				.csrf((csrf) -> csrf.disable())
				.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.build();
	}

	@Bean
	UserDetailsService apiOnlyUsers() {
		return SecurityUsers.demoUsers();
	}

}
