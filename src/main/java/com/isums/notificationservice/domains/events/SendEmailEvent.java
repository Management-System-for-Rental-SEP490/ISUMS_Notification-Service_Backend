package com.isums.notificationservice.domains.events;

import lombok.Builder;

import java.util.Map;

@Builder
public record SendEmailEvent(
        String to,
        String templateCode,
        Map<String, Object> params
) {}
