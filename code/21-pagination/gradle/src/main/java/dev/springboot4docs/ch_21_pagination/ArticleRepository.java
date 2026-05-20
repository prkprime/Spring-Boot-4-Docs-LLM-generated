package dev.springboot4docs.ch_21_pagination;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Page<Article> findByTitleContainingIgnoreCase(String q, Pageable pageable);

    Slice<Article> findByPublishedAtBefore(Instant before, Pageable pageable);

    List<Article> findTop20ByPublishedAtLessThanOrderByPublishedAtDescIdDesc(Instant cursor);

    Page<ArticleSummary> findSummariesByPublishedAtAfter(Instant after, Pageable pageable);

    @Query("""
            select new dev.springboot4docs.ch_21_pagination.ArticleTitleView(a.id, a.title, a.publishedAt)
            from Article a
            where a.publishedAt > :after
            """)
    Page<ArticleTitleView> findTitleViewsByPublishedAtAfter(@Param("after") Instant after, Pageable pageable);

}
