package com.isums.notificationservice.configurations;

import com.isums.userservice.grpc.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {
    @Bean
    UserServiceGrpc.UserServiceBlockingStub userStub(GrpcChannelFactory channels, GrpcServiceAuthInterceptor tokenInterceptor) {
        return UserServiceGrpc.newBlockingStub(channels.createChannel("user"))
                .withInterceptors(tokenInterceptor);
    }
}
