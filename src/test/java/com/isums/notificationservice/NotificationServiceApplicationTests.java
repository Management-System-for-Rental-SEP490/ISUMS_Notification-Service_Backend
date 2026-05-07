package com.isums.notificationservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires Postgres/Kafka/Redis/SMTP/gRPC infrastructure; run as integration test with Testcontainers")
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
