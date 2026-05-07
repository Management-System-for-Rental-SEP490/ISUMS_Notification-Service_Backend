package com.isums.notificationservice.infrastructures.grpcs;

import com.isums.houseservice.grpc.GetHouseRequest;
import com.isums.houseservice.grpc.GetRegionResponse;
import com.isums.houseservice.grpc.HouseResponse;
import com.isums.houseservice.grpc.HouseServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HouseGrpcClient {

    private final HouseServiceGrpc.HouseServiceBlockingStub houseStub;

    public UUID getLandlordIdByHouseId(UUID houseId) {
        HouseResponse response = houseStub.getHouseById(
                GetHouseRequest.newBuilder().setHouseId(houseId.toString()).build());

        if (response.getUserRentalId().isBlank()) {
            return null;
        }
        return UUID.fromString(response.getUserRentalId());
    }

    public UUID getManagerIdByHouseId(UUID houseId) {
        GetRegionResponse response = houseStub.getRegionIdByHouseId(
                GetHouseRequest.newBuilder().setHouseId(houseId.toString()).build());

        if (response.getManagerId().isBlank()) {
            return null;
        }
        return UUID.fromString(response.getManagerId());
    }

    /**
     * Friendly house name (e.g. "Nhà Quận 1", "Vinhomes Central Park").
     * Used in alert TTS / email so the recipient knows WHICH house the
     * incident is at — tenants can rent multiple houses, managers
     * supervise multiple regions, so the houseId UUID alone is useless
     * to a human listener. Returns "" if the house is missing or has
     * no name set.
     */
    public String getHouseNameByHouseId(UUID houseId) {
        try {
            HouseResponse response = houseStub.getHouseById(
                    GetHouseRequest.newBuilder().setHouseId(houseId.toString()).build());
            String name = response.getName();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }
}
