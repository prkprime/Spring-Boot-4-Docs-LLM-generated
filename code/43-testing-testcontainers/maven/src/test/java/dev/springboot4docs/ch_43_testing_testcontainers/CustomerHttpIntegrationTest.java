package dev.springboot4docs.ch_43_testing_testcontainers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class CustomerHttpIntegrationTest {

	@Autowired
	private RestTestClient rest;

	@Autowired
	private CustomerRepository customers;

	@BeforeEach
	void deleteExistingCustomers() {
		this.customers.deleteAll();
	}

	@Test
	void createsAndReadsCustomerThroughHttp() {
		this.rest.post().uri("/customers")
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{"email":"grace@example.com"}
					""")
			.exchange()
			.expectStatus().isCreated()
			.expectBody()
			.jsonPath("$.id").isNumber()
			.jsonPath("$.email").isEqualTo("grace@example.com")
			.jsonPath("$.createdAt").exists();

		this.rest.get().uri("/customers")
			.exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$[0].email").isEqualTo("grace@example.com");
	}

	@Test
	void returnsNotFoundForMissingCustomer() {
		this.rest.get().uri("/customers/{id}", 99_999)
			.exchange()
			.expectStatus().isNotFound();
	}

}
