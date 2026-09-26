package com.fahim.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class KeycloakAdminClientConfig {

    @Bean
    public RestClient keycloakAdminRestClient(
            RestClient.Builder builder, KeycloakAdminProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeoutMs());
        requestFactory.setReadTimeout(properties.readTimeoutMs());
        return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }
}
