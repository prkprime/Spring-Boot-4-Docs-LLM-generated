package dev.springboot4docs.ch_17_jpa_basics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuthorRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AuthorRepository authorRepository;

    @Test
    void findByEmailReturnsAuthor() {
        Author saved = persist("Octavia Butler", "octavia@example.com");

        assertThat(this.authorRepository.findByEmail("octavia@example.com"))
                .hasValueSatisfying(author -> assertThat(author.getId()).isEqualTo(saved.getId()));
    }

    @Test
    void findByNameContainingIgnoreCaseMatchesFragments() {
        persist("Ursula K. Le Guin", "ursula@example.com");
        persist("N. K. Jemisin", "nora@example.com");

        assertThat(this.authorRepository.findByNameContainingIgnoreCase("LE g"))
                .extracting(Author::getEmail)
                .containsExactly("ursula@example.com");
    }

    @Test
    void jpqlQueryFindsAuthorsByNamePrefix() {
        persist("Ada Palmer", "ada@example.com");
        persist("Ann Leckie", "ann@example.com");
        persist("Becky Chambers", "becky@example.com");

        assertThat(this.authorRepository.findByNamePrefix("A"))
                .extracting(Author::getName)
                .containsExactly("Ada Palmer", "Ann Leckie");
    }

    private Author persist(String name, String email) {
        Author author = new Author(name, email);
        this.entityManager.persistAndFlush(author);
        return author;
    }

}
