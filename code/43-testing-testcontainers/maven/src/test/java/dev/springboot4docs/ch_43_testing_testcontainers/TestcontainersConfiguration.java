package dev.springboot4docs.ch_43_testing_testcontainers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	static {
		postgres.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return postgres;
	}

}
