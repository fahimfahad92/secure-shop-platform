package com.fahim.orderservice.exception;

import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /**
     * The product is missing, not the order — from the caller's point of view that is a bad request
     * body, not a missing {@code /orders/{id}} resource, so this is 400 rather than 404.
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(
            InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /**
     * Product Service is down or erroring — the order is not rejected, it is simply not possible.
     */
    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleProductServiceUnavailable(
            ProductServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body(HttpStatus.BAD_REQUEST, message));
    }

    /**
     * No transaction could start. The client gets a generic answer that never names the database,
     * the pool or an exception, and never carries a stack trace; developers tell the cases apart by
     * the log level (finding #31):
     *
     * <ul>
     *   <li>Pool timeout with no cause: every connection was busy for longer than {@code
     *       spring.datasource.hikari.connection-timeout}. Load: WARN, message only, {@code 503}.
     *   <li>Pool timeout with a cause: Hikari attaches the last failure to open a connection, so
     *       the database is unreachable. Outage: ERROR with the cause's stack, the same {@code
     *       503}. Right after a short blip the old cause can linger until Hikari reconnects, so a
     *       busy timeout may then log at ERROR; that errs towards telling developers.
     *   <li>Anything else: ERROR with the stack, {@code 500}.
     * </ul>
     *
     * Both {@code 503} cases look the same to the client on purpose: its next move is the same
     * (retry later), and the difference is an internal detail.
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleNoTransaction(
            CannotCreateTransactionException ex) {
        SQLTransientConnectionException poolTimeout = findPoolTimeout(ex);
        if (poolTimeout == null) {
            log.error("Could not open a database transaction", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"));
        }
        if (poolTimeout.getCause() == null) {
            log.warn("DB pool exhausted: {}", poolTimeout.getMessage());
        } else {
            log.error("DB unreachable: {}", poolTimeout.getMessage(), poolTimeout.getCause());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(
                        body(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Service temporarily unavailable, try again later"));
    }

    private static SQLTransientConnectionException findPoolTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException poolTimeout) {
                return poolTimeout;
            }
        }
        return null;
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
