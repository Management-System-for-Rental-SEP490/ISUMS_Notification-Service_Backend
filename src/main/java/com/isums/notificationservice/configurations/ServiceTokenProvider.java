package com.isums.notificationservice.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceTokenProvider {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    private static final String REGISTRATION_ID = "user-service-client";
    private static final String PRINCIPAL_NAME = "notification-service";

    public String getAccessTokenValue() {
        OAuth2AuthorizeRequest req = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(PRINCIPAL_NAME)
                .build();

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(req);
        if (client == null) {
            throw new IllegalStateException("Cannot authorize OAuth2 client: " + REGISTRATION_ID);
        }

        OAuth2AccessToken token = client.getAccessToken();
        if (token == null) {
            throw new IllegalStateException("No access token for: " + REGISTRATION_ID);
        }

        return token.getTokenValue();
    }
}
