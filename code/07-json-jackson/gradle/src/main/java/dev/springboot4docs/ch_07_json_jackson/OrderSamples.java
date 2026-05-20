package dev.springboot4docs.ch_07_json_jackson;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class OrderSamples {

    public Order sample(Long id) {
        return new Order(id, Instant.parse("2026-05-09T10:15:30Z"), LocalDate.parse("2026-05-12"),
                new Money(new BigDecimal("12.50"), "USD"), OrderStatus.PAID,
                List.of(new Item("coffee-250g", 2), new Item("filter-100", 1)));
    }

}
