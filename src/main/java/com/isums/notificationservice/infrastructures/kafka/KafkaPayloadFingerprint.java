package com.isums.notificationservice.infrastructures.kafka;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class KafkaPayloadFingerprint {

    private KafkaPayloadFingerprint() {
    }

    public static String of(String topic, String payload) {
        String source = topic + "\u0000" + (payload == null ? "" : payload);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
