package dev.springboot4docs.ch_09_error_handling;

public record Order(Long id, String sku, int qty, String customerEmail) {
}
