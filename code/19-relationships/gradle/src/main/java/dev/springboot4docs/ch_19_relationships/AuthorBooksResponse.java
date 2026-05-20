package dev.springboot4docs.ch_19_relationships;

import java.util.List;

public record AuthorBooksResponse(String name, String email, List<BookResponse> books) {

    static AuthorBooksResponse from(Author author) {
        return new AuthorBooksResponse(author.getName(), author.getEmail(),
                author.getBooks().stream().map(BookResponse::from).toList());
    }

}
