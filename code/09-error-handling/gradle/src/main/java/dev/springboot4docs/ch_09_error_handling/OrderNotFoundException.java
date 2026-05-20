package dev.springboot4docs.ch_09_error_handling;

import lombok.Getter;

@Getter
public class OrderNotFoundException extends RuntimeException {

    private final long orderId;

    public OrderNotFoundException(long orderId) {
        super("Order " + orderId + " was not found");
        this.orderId = orderId;
    }

}
