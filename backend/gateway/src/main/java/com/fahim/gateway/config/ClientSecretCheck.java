package com.fahim.gateway.config;

import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fails startup when a client secret is missing.
 *
 * <p>Boot's property binding keeps an unresolvable {@code ${KEYCLOAK_..._CLIENT_SECRET}} as literal
 * text instead of failing, so without this check the Gateway starts normally and only breaks at
 * login, with Keycloak answering 401 to a secret that is the placeholder string itself. Typical
 * cause: the IDE was started before the environment variable was set.
 */
@Component
class ClientSecretCheck implements InitializingBean {

    private static final Map<String, String> SECRET_VARIABLES =
            Map.of(
                    "secure-shop-gateway", "KEYCLOAK_GATEWAY_CLIENT_SECRET",
                    "secure-shop-test-client", "KEYCLOAK_TEST_CLIENT_SECRET");

    private final ClientRegistrationRepository clientRegistrations;

    ClientSecretCheck(ClientRegistrationRepository clientRegistrations) {
        this.clientRegistrations = clientRegistrations;
    }

    @Override
    public void afterPropertiesSet() {
        if (!(this.clientRegistrations instanceof Iterable<?> registrations)) {
            return;
        }
        for (Object item : registrations) {
            if (item instanceof ClientRegistration registration) {
                check(registration);
            }
        }
    }

    private static void check(ClientRegistration registration) {
        String secret = registration.getClientSecret();
        if (StringUtils.hasText(secret) && !secret.contains("${")) {
            return;
        }
        String variable =
                SECRET_VARIABLES.getOrDefault(registration.getClientId(), "its client secret");
        throw new IllegalStateException(
                "Client secret for registration '"
                        + registration.getRegistrationId()
                        + "' ("
                        + registration.getClientId()
                        + ") is not set. Set "
                        + variable
                        + " (see docker/.env) and restart. An IDE only sees environment variables"
                        + " that existed when the IDE itself was started.");
    }
}
