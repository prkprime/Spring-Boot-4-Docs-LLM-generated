package dev.springboot4docs.ch_19_relationships;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.sql.init.mode=never"
})
class RelationshipsTest {

    @Autowired
    AuthorRepository authors;

    @Autowired
    EntityManager entityManager;

    Statistics stats;

    @BeforeEach
    void setUp() {
        this.stats = this.entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();

        seedAuthor("Octavia Butler", "octavia@example.com", "Kindred", 1979, "Parable of the Sower", 1993);
        seedAuthor("Ursula Le Guin", "ursula@example.com", "A Wizard of Earthsea", 1968, "The Dispossessed", 1974);
        seedAuthor("N. K. Jemisin", "nkj@example.com", "The Fifth Season", 2015, "The Obelisk Gate", 2016);

        this.entityManager.flush();
        this.entityManager.clear();
        this.stats.clear();
    }

    @Test
    void naiveFindAllTriggersOneQueryPlusOneQueryPerAuthorWhenBooksAreAccessed() {
        var result = this.authors.findAll();

        result.forEach((author) -> assertThat(author.getBooks()).hasSize(2));

        assertThat(this.stats.getPrepareStatementCount()).isEqualTo(4);
    }

    @Test
    void entityGraphFetchesAuthorsAndBooksWithOneQuery() {
        var result = this.authors.findAllWithBooks();

        result.forEach((author) -> assertThat(author.getBooks()).hasSize(2));

        assertThat(this.stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void fetchJoinFetchesAuthorsAndBooksWithOneQuery() {
        var result = this.authors.findAllWithBooksFetchJoin();

        result.forEach((author) -> assertThat(author.getBooks()).hasSize(2));

        assertThat(this.stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void projectionFetchesBookCountsWithOneQuery() {
        var counts = this.authors.findAuthorBookCounts();

        assertThat(this.stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(counts).hasSize(3);

        Map<String, Long> byName = counts.stream()
            .collect(Collectors.toMap(AuthorWithBookCount::name, AuthorWithBookCount::bookCount));
        assertThat(byName).containsEntry("Octavia Butler", 2L)
            .containsEntry("Ursula Le Guin", 2L)
            .containsEntry("N. K. Jemisin", 2L);
    }

    private void seedAuthor(String name, String email, String firstTitle, int firstYear, String secondTitle, int secondYear) {
        var author = new Author(name, email);
        author.addBook(new Book(firstTitle, firstYear));
        author.addBook(new Book(secondTitle, secondYear));
        this.entityManager.persist(author);
    }

}
