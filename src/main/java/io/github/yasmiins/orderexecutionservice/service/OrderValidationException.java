package io.github.yasmiins.orderexecutionservice.service;

public class OrderValidationException extends RuntimeException {

    public OrderValidationException(String message) {
        super(message);
    }
}
