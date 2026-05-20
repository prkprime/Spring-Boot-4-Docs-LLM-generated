package dev.springboot4docs.ch_38_logging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final LoggingDemoService loggingDemoService;

    public OrderController(LoggingDemoService loggingDemoService) {
        this.loggingDemoService = loggingDemoService;
    }

    @GetMapping("/orders/{id}")
    ResponseEntity<OrderResponse> getOrder(@PathVariable long id) {
        log.info("processing order {}", id);
        this.loggingDemoService.writeExampleLogs();

        OrderResponse order = findOrder(id);
        if (order == null) {
            log.warn("missing order {}", id);
            return ResponseEntity.notFound().build();
        }

        log.debug("found order {}", order);
        return ResponseEntity.ok(order);
    }

    private OrderResponse findOrder(long id) {
        Map<Long, OrderResponse> orders = Map.of(
                101L, new OrderResponse(101, "packing", "coffee beans"),
                202L, new OrderResponse(202, "shipped", "ceramic dripper"));
        return orders.get(id);
    }

    record OrderResponse(long id, String status, String item) {
    }

}
