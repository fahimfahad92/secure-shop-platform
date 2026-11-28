package com.fahim.orderservice.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderProperties.class)
public class OrderConfig {

    /**
     * One time source for a status change and for the cancel-window cutoff, so both come from the
     * same clock. Tests replace it with a fixed one.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
