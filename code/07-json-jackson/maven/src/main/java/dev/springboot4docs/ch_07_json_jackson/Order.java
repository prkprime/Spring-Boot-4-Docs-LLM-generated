package dev.springboot4docs.ch_07_json_jackson;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Order(Long id, Instant placedAt, LocalDate deliverBy, Money total, OrderStatus status, List<Item> items) {
}
