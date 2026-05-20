package dev.springboot4docs.ch_17_jpa_basics;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByEmail(String email);

    List<Author> findByNameContainingIgnoreCase(String fragment);

    @Query("select a from Author a where lower(a.name) like lower(concat(:prefix, '%')) order by a.name")
    List<Author> findByNamePrefix(String prefix);

}
