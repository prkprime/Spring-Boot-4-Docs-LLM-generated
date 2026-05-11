package dev.springboot4docs.ch_32_jwt_resource_server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
		return http
				.csrf((csrf) -> csrf.disable())
				.sessionManagement((sessions) -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests((requests) -> requests
						.requestMatchers("/public/**").permitAll()
						.requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer((resourceServer) -> resourceServer
						.jwt((jwt) -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.build();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopeAuthorities = new JwtGrantedAuthoritiesConverter();
		Converter<Jwt, Collection<GrantedAuthority>> roleAuthorities = this::roleAuthorities;

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter((jwt) -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>(scopeAuthorities.convert(jwt));
			authorities.addAll(roleAuthorities.convert(jwt));
			return authorities;
		});
		return converter;
	}

	private Collection<GrantedAuthority> roleAuthorities(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		if (roles == null) {
			return List.of();
		}
		return roles.stream()
				.map((role) -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
				.map(SimpleGrantedAuthority::new)
				.map(GrantedAuthority.class::cast)
				.toList();
	}

}
