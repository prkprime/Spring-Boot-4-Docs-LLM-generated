package dev.springboot4docs.ch_18_postgres_testcontainers;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorService {

    private final AuthorRepository authorRepository;

    public AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    public List<Author> findAll() {
        return this.authorRepository.findAll();
    }

    public Optional<Author> findById(Long id) {
        return this.authorRepository.findById(id);
    }

    @Transactional
    public Author create(String name, String email, String bio) {
        return this.authorRepository.save(new Author(name, email, bio));
    }

    @Transactional
    public void deleteById(Long id) {
        this.authorRepository.findById(id).ifPresent(this.authorRepository::delete);
    }

}
