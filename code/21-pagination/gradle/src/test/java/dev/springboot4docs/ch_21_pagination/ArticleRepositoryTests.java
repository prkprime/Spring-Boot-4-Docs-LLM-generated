package dev.springboot4docs.ch_21_pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

@DataJpaTest
class ArticleRepositoryTests {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ArticleRepository articles;

    @BeforeEach
    void seedArticles() {
        List<Article> seed = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            seed.add(new Article("Spring Data pagination " + i, "Body " + i, BASE_TIME.plusSeconds(i * 60L)));
        }
        this.articles.saveAll(seed);
    }

    @Test
    void pageQueryIncludesTotalMetadata() {
        PageRequest request = PageRequest.of(1, 10, Sort.by("publishedAt").descending());

        Page<Article> page = this.articles.findByTitleContainingIgnoreCase("pagination", request);

        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(50);
        assertThat(page.getTotalPages()).isEqualTo(5);
        assertThat(page.getContent()).isSortedAccordingTo(Comparator.comparing(Article::getPublishedAt).reversed());
    }

    @Test
    void sliceQueryReportsWhetherAnotherSliceExists() {
        PageRequest request = PageRequest.of(0, 10, Sort.by("publishedAt").descending());

        Slice<Article> slice = this.articles.findByPublishedAtBefore(BASE_TIME.plusSeconds(51 * 60L), request);

        assertThat(slice.getContent()).hasSize(10);
        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.getNumber()).isZero();
    }

    @Test
    void keysetQueryMovesForwardFromTheLastSeenCursor() {
        List<Article> firstPage = this.articles.findTop20ByPublishedAtLessThanOrderByPublishedAtDescIdDesc(
                BASE_TIME.plusSeconds(51 * 60L));
        Instant cursor = firstPage.getLast().getPublishedAt();

        List<Article> secondPage = this.articles.findTop20ByPublishedAtLessThanOrderByPublishedAtDescIdDesc(cursor);

        assertThat(firstPage).hasSize(20);
        assertThat(secondPage).hasSize(20);
        assertThat(firstPage.getFirst().getTitle()).isEqualTo("Spring Data pagination 50");
        assertThat(firstPage.getLast().getTitle()).isEqualTo("Spring Data pagination 31");
        assertThat(secondPage.getFirst().getTitle()).isEqualTo("Spring Data pagination 30");
        assertThat(secondPage.getLast().getTitle()).isEqualTo("Spring Data pagination 11");
    }

    @Test
    void projectionsReturnOnlyTheRequestedView() {
        PageRequest request = PageRequest.of(0, 5, Sort.by("publishedAt").descending());

        Page<ArticleSummary> summaries = this.articles.findSummariesByPublishedAtAfter(BASE_TIME, request);
        Page<ArticleTitleView> titleViews = this.articles.findTitleViewsByPublishedAtAfter(BASE_TIME, request);

        assertThat(summaries.getContent()).hasSize(5);
        assertThat(summaries.getContent().getFirst().getTitle()).isEqualTo("Spring Data pagination 50");
        assertThat(titleViews.getContent()).hasSize(5);
        assertThat(titleViews.getContent().getFirst().title()).isEqualTo("Spring Data pagination 50");
    }

}
