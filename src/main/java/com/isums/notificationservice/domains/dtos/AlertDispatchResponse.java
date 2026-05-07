package com.isums.notificationservice.domains.dtos;

import java.util.List;
import java.util.UUID;

public record AlertDispatchResponse(
        boolean ok,
        UUID userId,
        List<ChannelDispatchResult> results
) {
    public record ChannelDispatchResult(
            String channel,
            String status,    // SENT | SKIPPED | FAILED
            String reason,    // only set when not SENT
            UUID voiceJobId   // only for VOICE channel
    ) {}
}
