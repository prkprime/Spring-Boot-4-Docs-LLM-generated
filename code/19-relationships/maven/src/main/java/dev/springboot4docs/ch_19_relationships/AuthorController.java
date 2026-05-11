package dev.springboot4docs.ch_19_relationships;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/authors")
public class AuthorController {

	private final AuthorReadService authors;

	public AuthorController(AuthorReadService authors) {
		this.authors = authors;
	}

	@GetMapping("/naive")
	List<AuthorBooksResponse> naive() {
		return this.authors.findAllNaive();
	}

	@GetMapping("/entity-graph")
	List<AuthorBooksResponse> entityGraph() {
		return this.authors.findAllWithEntityGraph();
	}

	@GetMapping("/fetch-join")
	List<AuthorBooksResponse> fetchJoin() {
		return this.authors.findAllWithFetchJoin();
	}

	@GetMapping("/book-counts")
	List<AuthorWithBookCount> bookCounts() {
		return this.authors.findBookCounts();
	}

}
