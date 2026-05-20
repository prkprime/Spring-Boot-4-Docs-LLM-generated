package dev.springboot4docs.ch_21_pagination;

import java.time.Instant;

public record ArticleTitleView(Long id, String title, Instant publishedAt) {
}
