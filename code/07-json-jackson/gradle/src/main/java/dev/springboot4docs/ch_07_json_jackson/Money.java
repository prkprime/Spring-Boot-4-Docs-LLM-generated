package dev.springboot4docs.ch_07_json_jackson;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
}
