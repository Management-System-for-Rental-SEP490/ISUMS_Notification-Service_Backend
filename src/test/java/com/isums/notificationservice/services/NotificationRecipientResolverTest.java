package com.isums.notificationservice.services;

import com.isums.notificationservice.infrastructures.grpcs.HouseGrpcClient;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientResolverTest {

    @Mock
    private HouseGrpcClient houseGrpcClient;

    @Mock
    private UserGrpcClient userGrpcClient;

    @InjectMocks
    private NotificationRecipientResolver resolver;

    @Test
    void resolvesConfiguredLandlordAndInternalManagerToJwtSubjectsAndDeduplicates() {
        UUID houseId = UUID.randomUUID();
        UUID landlordKeycloakId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        UUID managerKeycloakId = UUID.randomUUID();

        ReflectionTestUtils.setField(resolver, "landlordKeycloakId", landlordKeycloakId.toString());
        when(houseGrpcClient.getManagerIdByHouseId(houseId)).thenReturn(managerUserId);
        when(userGrpcClient.getUserById(managerUserId))
                .thenReturn(user(managerUserId, managerKeycloakId));

        List<UUID> recipients = resolver.resolveLandlordAndManager(houseId, managerUserId);

        assertThat(recipients).containsExactly(landlordKeycloakId, managerKeycloakId);
    }

    private static UserResponse user(UUID internalId, UUID keycloakId) {
        return UserResponse.newBuilder()
                .setId(internalId.toString())
                .setKeycloakId(keycloakId.toString())
                .build();
    }
}
