package dev.springboot4docs.ch_21_pagination;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArticleController {

    private final ArticleRepository articles;

    public ArticleController(ArticleRepository articles) {
        this.articles = articles;
    }

    @GetMapping("/articles")
    Page<Article> search(@RequestParam(defaultValue = "") String q, Pageable pageable) {
        return this.articles.findByTitleContainingIgnoreCase(q, pageable);
    }

    @GetMapping("/articles/cursor")
    CursorPage<Article> cursor(@RequestParam Instant after, @RequestParam(defaultValue = "20") int limit) {
        int cappedLimit = Math.clamp(limit, 1, 20);
        List<Article> fetched = this.articles.findTop20ByPublishedAtLessThanOrderByPublishedAtDescIdDesc(after);
        List<Article> page = fetched.stream().limit(cappedLimit).toList();
        Instant nextCursor = page.size() == cappedLimit ? page.getLast().getPublishedAt() : null;
        return new CursorPage<>(page, nextCursor);
    }

    public record CursorPage<T>(List<T> items, Instant nextCursor) {
    }

}
