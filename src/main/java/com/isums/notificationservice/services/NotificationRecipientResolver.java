package com.isums.notificationservice.services;

import com.isums.notificationservice.infrastructures.grpcs.HouseGrpcClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationRecipientResolver {

    private final HouseGrpcClient houseGrpcClient;

    public List<UUID> resolveLandlordAndManager(UUID houseId, UUID... extraRecipientIds) {
        Set<UUID> recipientIds = new LinkedHashSet<>();

        if (houseId != null) {
            UUID landlordId = houseGrpcClient.getLandlordIdByHouseId(houseId);
            if (landlordId != null) {
                recipientIds.add(landlordId);
            }

            UUID managerId = houseGrpcClient.getManagerIdByHouseId(houseId);
            if (managerId != null) {
                recipientIds.add(managerId);
            }
        }

        if (extraRecipientIds != null) {
            for (UUID extraRecipientId : extraRecipientIds) {
                if (extraRecipientId != null) {
                    recipientIds.add(extraRecipientId);
                }
            }
        }

        return new ArrayList<>(recipientIds);
    }
}
