package dev.springboot4docs.ch_19_relationships;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorReadService {

    private final AuthorRepository authors;

    public AuthorReadService(AuthorRepository authors) {
        this.authors = authors;
    }

    public List<AuthorBooksResponse> findAllNaive() {
        return this.authors.findAll().stream().map(AuthorBooksResponse::from).toList();
    }

    public List<AuthorBooksResponse> findAllWithEntityGraph() {
        return this.authors.findAllWithBooks().stream().map(AuthorBooksResponse::from).toList();
    }

    public List<AuthorBooksResponse> findAllWithFetchJoin() {
        return this.authors.findAllWithBooksFetchJoin().stream().map(AuthorBooksResponse::from).toList();
    }

    public List<AuthorWithBookCount> findBookCounts() {
        return this.authors.findAuthorBookCounts();
    }

}
