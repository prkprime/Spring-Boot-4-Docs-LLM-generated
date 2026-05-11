package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf((csrf) -> csrf.disable())
				.authorizeHttpRequests((requests) -> requests
						.requestMatchers("/api/passkeys/**", "/h2-console/**").permitAll()
						.anyRequest().denyAll())
				.headers((headers) -> headers.frameOptions((frameOptions) -> frameOptions.sameOrigin()))
				.build();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	UserDetailsService users() {
		return new InMemoryUserDetailsManager();
	}

}
