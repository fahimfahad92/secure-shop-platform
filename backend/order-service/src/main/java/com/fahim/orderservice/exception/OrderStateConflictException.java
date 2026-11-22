package com.fahim.orderservice.exception;

/** The order exists, but its current status doesn't allow what was asked. Answered with 409. */
public class OrderStateConflictException extends RuntimeException {

    public OrderStateConflictException(String message) {
        super(message);
    }
}
