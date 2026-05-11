package dev.springboot4docs.ch_19_relationships;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorRepository extends JpaRepository<Author, Long> {

	@EntityGraph(attributePaths = "books")
	@Query("select a from Author a")
	List<Author> findAllWithBooks();

	@Query("select a from Author a left join fetch a.books")
	List<Author> findAllWithBooksFetchJoin();

	@Query("""
			select new dev.springboot4docs.ch_19_relationships.AuthorWithBookCount(a.name, count(b))
			from Author a
			left join a.books b
			group by a.id
			""")
	List<AuthorWithBookCount> findAuthorBookCounts();

}
