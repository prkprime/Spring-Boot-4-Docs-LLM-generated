package dev.springboot4docs.ch_27_http_basic;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/**").authenticated()
						.requestMatchers("/", "/public/**").permitAll()
						.anyRequest().denyAll())
				.httpBasic(Customizer.withDefaults())
				.csrf((csrf) -> csrf.disable())
				.build();
	}

	@Bean
	InMemoryUserDetailsManager users() {
		PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		UserDetails alice = User.withUsername("alice")
				.password(encoder.encode("secret"))
				.roles("USER")
				.build();
		UserDetails bob = User.withUsername("bob")
				.password(encoder.encode("secret"))
				.roles("ADMIN", "USER")
				.build();

		return new InMemoryUserDetailsManager(alice, bob);
	}

}
