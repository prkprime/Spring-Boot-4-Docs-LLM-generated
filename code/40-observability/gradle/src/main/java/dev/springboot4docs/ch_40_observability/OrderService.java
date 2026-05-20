package dev.springboot4docs.ch_40_observability;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final AtomicLong orderIds = new AtomicLong();

    private final MeterRegistry meterRegistry;

    private final Timer processTimer;

    public OrderService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.processTimer = Timer.builder("orders.process.duration")
                .description("Time spent processing orders")
                .register(meterRegistry);
    }

    @Observed(name = "order.process")
    public OrderReceipt process(Order order) {
        return this.processTimer.record(() -> {
            try {
                validate(order);
                incrementCounter("success");
                return new OrderReceipt(this.orderIds.incrementAndGet(), "accepted");
            }
            catch (RuntimeException ex) {
                incrementCounter("failure");
                throw ex;
            }
        });
    }

    private void incrementCounter(String outcome) {
        Counter.builder("orders.processed.total")
                .description("Number of processed orders")
                .tag("outcome", outcome)
                .register(this.meterRegistry)
                .increment();
    }

    private static void validate(Order order) {
        if (order == null || order.sku() == null || order.sku().isBlank()) {
            throw new IllegalArgumentException("sku is required");
        }
        if (order.quantity() < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

}
