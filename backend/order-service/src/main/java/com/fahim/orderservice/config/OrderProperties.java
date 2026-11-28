package com.fahim.orderservice.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Order rules that are configuration, not code.
 *
 * @param cancelWindow how long after confirmation an order may still be cancelled (ADR-0016)
 */
@Validated
@ConfigurationProperties("orders")
public record OrderProperties(@NotNull Duration cancelWindow) {

    public OrderProperties {
        if (cancelWindow != null && (cancelWindow.isZero() || cancelWindow.isNegative())) {
            throw new IllegalArgumentException("orders.cancel-window must be positive");
        }
    }
}
