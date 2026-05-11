package dev.springboot4docs.ch_28_form_login;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
						.requestMatchers("/", "/login", "/css/**").permitAll()
						.anyRequest().authenticated())
				.formLogin((form) -> form
						.loginPage("/login")
						.defaultSuccessUrl("/dashboard", true)
						.permitAll())
				.logout((logout) -> logout.permitAll())
				.build();
	}

	@Bean
	InMemoryUserDetailsManager users() {
		PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		UserDetails alice = User.withUsername("alice")
				.password(encoder.encode("secret"))
				.roles("USER")
				.build();

		return new InMemoryUserDetailsManager(alice);
	}

}
