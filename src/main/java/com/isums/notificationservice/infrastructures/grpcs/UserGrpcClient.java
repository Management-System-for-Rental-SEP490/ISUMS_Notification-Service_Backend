package com.isums.notificationservice.infrastructures.grpcs;

import com.isums.userservice.grpc.GetUserByIdRequest;
import com.isums.userservice.grpc.UserResponse;
import com.isums.userservice.grpc.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserGrpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserResponse getUserById(UUID userId) {

        GetUserByIdRequest req = GetUserByIdRequest.newBuilder().setUserId(String.valueOf(userId)).build();

        return userStub.getUserById(req);
    }
}
