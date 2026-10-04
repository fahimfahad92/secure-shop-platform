package com.fahim.gateway.devlogin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Dev login beans exist only with {@code secure-shop.dev-login.enabled=true}, which only {@code
 * application-dev.properties} sets. Off by default, so a normal start has no password endpoint.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(name = "secure-shop.dev-login.enabled", havingValue = "true")
@interface ConditionalOnDevLogin {}
