package dev.springboot4docs.ch_19_relationships;

public record BookResponse(String title, Integer publicationYear) {

    static BookResponse from(Book book) {
        return new BookResponse(book.getTitle(), book.getPublicationYear());
    }

}
