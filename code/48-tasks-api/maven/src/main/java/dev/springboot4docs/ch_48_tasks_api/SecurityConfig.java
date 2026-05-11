package dev.springboot4docs.ch_48_tasks_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf((csrf) -> csrf.disable())
				.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests((requests) -> requests
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/tasks/**").hasAuthority("SCOPE_tasks.read")
						.requestMatchers("/api/tasks/**").hasAuthority("SCOPE_tasks.write")
						.anyRequest().denyAll())
				.oauth2ResourceServer((resourceServer) -> resourceServer.jwt((jwt) -> {
				}))
				.build();
	}

}
