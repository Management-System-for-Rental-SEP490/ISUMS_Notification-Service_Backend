package com.isums.notificationservice.domains.dtos;

import com.isums.notificationservice.domains.enums.AlertEventType;

import java.util.Map;
import java.util.UUID;

/**
 * Payload the IoT Lambda tier (esp32-threshold-checker / esp32-eif-score)
 * POSTs to {@code /api/notifications/internal/dispatch} for multi-channel
 * delivery to the tenant + landlord.
 *
 * <p>{@code userId} is the tenant; the Notification-Service resolves
 * landlord via HouseGrpc fallback when escalation_target_user_id is unset.
 *
 * <p>{@code templateVars} are merged with standard fields (areaName, value,
 * unit) for Mustache interpolation.
 */
public record AlertDispatchRequest(
        UUID userId,
        String alertId,
        AlertEventType eventType,
        String houseId,
        String areaId,
        String areaName,
        String thing,
        String metric,
        Double value,
        String unit,
        Map<String, Object> templateVars
) {}
