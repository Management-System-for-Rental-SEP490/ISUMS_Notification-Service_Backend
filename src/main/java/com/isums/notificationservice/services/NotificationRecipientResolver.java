package com.isums.notificationservice.services;

import com.isums.notificationservice.infrastructures.grpcs.HouseGrpcClient;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRecipientResolver {

    private final HouseGrpcClient houseGrpcClient;
    private final UserGrpcClient userGrpcClient;

    @Value("${app.notification.landlord-keycloak-id:}")
    private String landlordKeycloakId;

    public List<UUID> resolveLandlordAndManager(UUID houseId, UUID... extraRecipientIds) {
        Set<UUID> recipientIds = new LinkedHashSet<>();

        if (houseId != null) {
            addConfiguredLandlord(recipientIds);
            addCanonicalRecipient(recipientIds, houseGrpcClient.getManagerIdByHouseId(houseId));
        }

        if (extraRecipientIds != null) {
            for (UUID extraRecipientId : extraRecipientIds) {
                addCanonicalRecipient(recipientIds, extraRecipientId);
            }
        }

        return new ArrayList<>(recipientIds);
    }

    private void addConfiguredLandlord(Set<UUID> recipientIds) {
        if (landlordKeycloakId == null || landlordKeycloakId.isBlank()) {
            log.warn("Landlord Keycloak ID is not configured; landlord notification will be skipped");
            return;
        }
        try {
            recipientIds.add(UUID.fromString(landlordKeycloakId));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid configured landlord Keycloak ID", e);
        }
    }

    private void addCanonicalRecipient(Set<UUID> recipientIds, UUID userId) {
        if (userId == null) {
            return;
        }

        UserResponse user;
        try {
            user = userGrpcClient.getUserById(userId);
        } catch (Exception internalLookupFailed) {
            log.debug("Recipient {} is not an internal user ID; trying Keycloak ID", userId);
            user = userGrpcClient.getUserByKeycloakId(userId.toString());
        }

        if (user == null || user.getKeycloakId().isBlank()) {
            throw new IllegalStateException("User has no Keycloak ID: " + userId);
        }

        try {
            recipientIds.add(UUID.fromString(user.getKeycloakId()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Keycloak ID for user " + userId, e);
        }
    }
}
