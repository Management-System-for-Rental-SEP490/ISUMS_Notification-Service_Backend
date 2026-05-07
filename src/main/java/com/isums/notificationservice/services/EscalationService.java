package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.entities.VoiceCallEscalation;
import com.isums.notificationservice.domains.enums.EscalationReason;
import com.isums.notificationservice.infrastructures.grpcs.HouseGrpcClient;
import com.isums.notificationservice.infrastructures.repositories.VoiceCallEscalationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationService {

    private final HouseGrpcClient houseGrpcClient;
    private final VoiceCallEscalationRepository escalationRepo;

    /**
     * Decides who to escalate to. Priority:
     *   1. user-specified {@code escalation_target_user_id} in preferences
     *   2. MANAGER of the alert's house's region (HouseGrpc) — operations
     *      contact, paid by the landlord to handle on-call incidents
     *   3. landlord, only as a final fallback when the region has no
     *      assigned manager (rare — usually a setup gap)
     *   4. null → nothing to escalate to, caller drops the escalation
     *
     * <p>Why manager over landlord? B2B reality: the landlord OWNS the
     * property and pays for the service, but the MANAGER is the on-call
     * operations contact. Calling the landlord at 2am for every gas
     * sensor blip would burn the relationship — landlord just wants
     * email visibility (see ChannelPolicy LANDLORD = email-only).
     */
    public UUID resolveEscalationTarget(UUID originalUserId,
                                         UserNotificationPreferences prefs,
                                         String houseIdOrNull) {
        if (prefs.getEscalationTargetUserId() != null) {
            return prefs.getEscalationTargetUserId();
        }
        if (houseIdOrNull == null || houseIdOrNull.isBlank()) {
            return null;
        }
        UUID houseUuid;
        try { houseUuid = UUID.fromString(houseIdOrNull); }
        catch (IllegalArgumentException e) { return null; }

        // Primary path: region manager.
        try {
            UUID manager = houseGrpcClient.getManagerIdByHouseId(houseUuid);
            if (manager != null && !manager.equals(originalUserId)) {
                return manager;
            }
        } catch (Exception e) {
            log.warn("[Escalation] manager lookup failed houseId={}: {}",
                    houseIdOrNull, e.getMessage());
        }
        // Fallback: landlord (only if region has no manager assigned).
        try {
            UUID landlord = houseGrpcClient.getLandlordIdByHouseId(houseUuid);
            if (landlord != null && !landlord.equals(originalUserId)) {
                log.info("[Escalation] no manager for houseId={}, falling back to landlord", houseIdOrNull);
                return landlord;
            }
        } catch (Exception e) {
            log.warn("[Escalation] landlord lookup failed houseId={}: {}",
                    houseIdOrNull, e.getMessage());
        }
        return null;
    }

    @Transactional
    public VoiceCallEscalation record(UUID originalCallId, UUID escalatedCallId,
                                        UUID escalatedToUserId, EscalationReason reason) {
        VoiceCallEscalation row = VoiceCallEscalation.builder()
                .originalCallId(originalCallId)
                .escalatedCallId(escalatedCallId)
                .escalatedToUserId(escalatedToUserId)
                .reason(reason)
                .build();
        return escalationRepo.save(row);
    }
}
