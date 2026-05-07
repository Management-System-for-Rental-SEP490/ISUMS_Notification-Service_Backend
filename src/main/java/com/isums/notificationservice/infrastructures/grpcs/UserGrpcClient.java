package com.isums.notificationservice.infrastructures.grpcs;

import com.isums.userservice.grpc.GetUserByIdRequest;
import com.isums.userservice.grpc.GetUserIdAndRoleByKeyCloakIdRequest;
import com.isums.userservice.grpc.UserResponse;
import com.isums.userservice.grpc.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserGrpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    /**
     * Lookup by INTERNAL user UUID (the {@code users.id} primary key).
     * Use this when the caller already holds an internal ID — typically
     * an escalation target picked from the {@code /api/users/managers}
     * REST endpoint, which returns internal IDs.
     */
    public UserResponse getUserById(UUID userId) {

        GetUserByIdRequest req = GetUserByIdRequest.newBuilder().setUserId(String.valueOf(userId)).build();

        return userStub.getUserById(req);
    }

    /**
     * Lookup by Keycloak {@code sub} claim — the value mobile/web JWTs
     * carry. Use this whenever the caller derived the ID from a JWT
     * (which is most controller paths). Returns the same UserResponse
     * shape so call-sites stay symmetric with {@link #getUserById}.
     */
    public UserResponse getUserByKeycloakId(String keycloakId) {
        GetUserIdAndRoleByKeyCloakIdRequest req =
                GetUserIdAndRoleByKeyCloakIdRequest.newBuilder()
                        .setKeycloakId(keycloakId)
                        .build();
        return userStub.getUserIdAndRoleByKeyCloakId(req);
    }
}
