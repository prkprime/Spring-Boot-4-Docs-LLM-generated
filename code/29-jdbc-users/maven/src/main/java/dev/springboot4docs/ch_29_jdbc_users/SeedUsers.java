package dev.springboot4docs.ch_29_jdbc_users;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class SeedUsers {

	@Bean
	@Profile("!test")
	CommandLineRunner seedAdminUser(AppUserRepository users, PasswordEncoder passwordEncoder) {
		return (args) -> users.findByUsername("admin")
				.orElseGet(() -> users.save(new AppUser("admin", passwordEncoder.encode("secret"), "USER,ADMIN")));
	}

}
