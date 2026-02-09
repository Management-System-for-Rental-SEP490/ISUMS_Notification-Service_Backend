package com.isums.notificationservice.infrastructures.grpcs;

import com.isums.notificationservice.grpc.GetUserRequest;
import com.isums.notificationservice.grpc.UserResponse;
import com.isums.notificationservice.grpc.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserGrpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserResponse getUserById(UUID userId) {

        GetUserRequest req = GetUserRequest.newBuilder().setUserId(String.valueOf(userId)).build();

        return userStub.getUserById(req);
    }
}
