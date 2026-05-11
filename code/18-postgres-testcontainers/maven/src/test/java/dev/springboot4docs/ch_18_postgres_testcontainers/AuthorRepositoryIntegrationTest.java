package dev.springboot4docs.ch_18_postgres_testcontainers;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = NONE)
class AuthorRepositoryIntegrationTest {

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private AuthorRepository authorRepository;

	@Test
	void flywaySeedDataIsAvailable() {
		assertThat(this.authorRepository.findByEmail("ursula@example.com"))
				.hasValueSatisfying(author -> {
					assertThat(author.getName()).isEqualTo("Ursula K. Le Guin");
					assertThat(author.getBio()).contains("speculative fiction");
					assertThat(author.getCreatedAt()).isNotNull();
				});
	}

	@Test
	void savesAndFindsAuthorInPostgres() {
		Author saved = persist("Ada Palmer", "ada@example.com", "Historian and speculative fiction author.");

		assertThat(this.authorRepository.findByEmail("ada@example.com"))
				.hasValueSatisfying(author -> assertThat(author.getId()).isEqualTo(saved.getId()));
	}

	@Test
	void postgresBackedQueriesMatchTextCaseInsensitively() {
		persist("Ann Leckie", "ann@example.com", "Author of space opera.");

		assertThat(this.authorRepository.findByNameContainingIgnoreCase("LECK"))
				.extracting(Author::getEmail)
				.contains("ann@example.com");
	}

	private Author persist(String name, String email, String bio) {
		Author author = new Author(name, email, bio);
		this.entityManager.persistAndFlush(author);
		return author;
	}

}
