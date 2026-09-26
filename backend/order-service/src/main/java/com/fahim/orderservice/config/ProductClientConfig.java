package com.fahim.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ProductClientConfig {

    @Bean
    public RestClient productRestClient(
            RestClient.Builder builder,
            @Value("${product-service.base-url}") String baseUrl,
            @Value("${product-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${product-service.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
