package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.NotificationPreferencesDto;
import com.isums.notificationservice.domains.dtos.UpdatePreferencesRequest;
import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.entities.VoiceConsentHistory;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import com.isums.notificationservice.exceptions.ConflictException;
import com.isums.notificationservice.infrastructures.repositories.NotificationSubscriptionRepository;
import com.isums.notificationservice.infrastructures.repositories.UserNotificationPreferencesRepository;
import com.isums.notificationservice.infrastructures.repositories.VoiceConsentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceService {

    private final UserNotificationPreferencesRepository prefsRepo;
    private final NotificationSubscriptionRepository subsRepo;
    private final VoiceConsentHistoryRepository consentHistoryRepo;

    /**
     * Current published version of the voice consent T&C text. When
     * legal updates the wording, bump this — existing consents stay
     * valid for already-active users but new grants record the new
     * version. Source of truth: {@code docs/legal/voice-consent-vi.md}.
     */
    private static final String CURRENT_CONSENT_VERSION = "v1.0-2026-04";

    @Transactional
    public UserNotificationPreferences getOrCreate(UUID userId) {
        return prefsRepo.findById(userId)
                .orElseGet(() -> {
                    try {
                        return prefsRepo.saveAndFlush(
                                UserNotificationPreferences.builder().userId(userId).build());
                    } catch (DataIntegrityViolationException race) {
                        // Concurrent request created the row first (e.g. two
                        // SSE subscribes from the same user, two browser tabs).
                        // Retry the read instead of bubbling the unique-key
                        // violation up to the user.
                        log.debug("[Prefs] race on getOrCreate userId={} — re-reading", userId);
                        return prefsRepo.findById(userId)
                                .orElseThrow(() -> race);
                    }
                });
    }

    @Transactional
    public NotificationSubscription getSubscriptionOrCreate(UUID userId) {
        return subsRepo.findById(userId)
                .orElseGet(() -> {
                    try {
                        return subsRepo.saveAndFlush(
                                NotificationSubscription.builder()
                                        .userId(userId)
                                        .tier(SubscriptionTier.FREE)
                                        .voiceQuotaMonthly(TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.FREE))
                                        .smsQuotaMonthly(TierQuotaPolicy.smsQuotaFor(SubscriptionTier.FREE))
                                        .build());
                    } catch (DataIntegrityViolationException race) {
                        // Same race-condition guard as getOrCreate above —
                        // duplicate-pkey on user_id means another tx beat us
                        // to it; just read the row that's now there.
                        log.debug("[Sub] race on getSubscriptionOrCreate userId={} — re-reading", userId);
                        return subsRepo.findById(userId)
                                .orElseThrow(() -> race);
                    }
                });
    }

    @Transactional
    public UserNotificationPreferences update(UUID userId, UpdatePreferencesRequest req) {
        return update(userId, req, false, null, null);
    }

    @Transactional
    public UserNotificationPreferences update(UUID userId, UpdatePreferencesRequest req,
                                                boolean tierExempt) {
        return update(userId, req, tierExempt, null, null);
    }

    /**
     * Full update path — captures consent metadata (IP, UA, T&C version)
     * for PDPL audit when {@code voiceConsentGranted} flips. Pass null
     * for {@code clientIp} / {@code userAgent} from non-HTTP callers
     * (Kafka listener, scheduled job).
     */
    @Transactional
    public UserNotificationPreferences update(UUID userId, UpdatePreferencesRequest req,
                                                boolean tierExempt,
                                                String clientIp,
                                                String userAgent) {
        UserNotificationPreferences p = getOrCreate(userId);
        NotificationSubscription sub = getSubscriptionOrCreate(userId);

        if (req.language() != null)                    p.setLanguage(req.language());
        if (req.emailEnabled() != null)                p.setEmailEnabled(req.emailEnabled());
        if (req.pushEnabled() != null)                 p.setPushEnabled(req.pushEnabled());
        if (req.smsEnabled() != null)                  p.setSmsEnabled(req.smsEnabled());

        if (req.voiceEnabled() != null) {
            // Gate voice on tier AND consent — silent rejection is worse than a 409,
            // because the user would think it's on but never receives calls.
            if (req.voiceEnabled()) {
                if (!tierExempt) {
                    // Tenant path — paid subscription + explicit TCPA-style consent.
                    if (sub.getTier() != SubscriptionTier.PREMIUM) {
                        throw new ConflictException(
                                "Voice notifications require PREMIUM subscription. "
                                + "Upgrade at /api/notifications/subscriptions/upgrade");
                    }
                    if (p.getVoiceConsentGivenAt() == null
                            && !Boolean.TRUE.equals(req.voiceConsentGranted())) {
                        throw new ConflictException(
                                "Voice calls require explicit consent. "
                                + "Pass voiceConsentGranted=true together with voiceEnabled=true.");
                    }
                } else if (p.getVoiceConsentGivenAt() == null) {
                    // Landlord / manager — implicit consent via employment.
                    // Stamp the timestamp anyway so the audit trail records
                    // when voice was first activated for that user.
                    p.setVoiceConsentGivenAt(Instant.now());
                }
            }
            p.setVoiceEnabled(req.voiceEnabled());
        }

        if (req.quietHoursEnabled() != null)           p.setQuietHoursEnabled(req.quietHoursEnabled());
        if (req.quietHoursStart() != null)             p.setQuietHoursStart(req.quietHoursStart());
        if (req.quietHoursEnd() != null)               p.setQuietHoursEnd(req.quietHoursEnd());
        if (req.quietHoursOverrideCritical() != null)  p.setQuietHoursOverrideCritical(req.quietHoursOverrideCritical());

        if (req.voiceMaxRetries() != null) {
            int cap = TierQuotaPolicy.maxVoiceRetries(sub.getTier());
            p.setVoiceMaxRetries(Math.min(req.voiceMaxRetries(), cap));
        }
        if (req.voiceRetryIntervalSec() != null) {
            int min = TierQuotaPolicy.minRetryIntervalSec(sub.getTier());
            p.setVoiceRetryIntervalSec(Math.max(req.voiceRetryIntervalSec(), min));
        }
        if (req.voiceRateLimitSec() != null)           p.setVoiceRateLimitSec(req.voiceRateLimitSec());
        if (req.voiceGender() != null)                 p.setVoiceGender(req.voiceGender());
        if (req.voiceSpeed() != null)                  p.setVoiceSpeed(req.voiceSpeed());
        if (req.dtmfAckEnabled() != null)              p.setDtmfAckEnabled(req.dtmfAckEnabled());
        if (req.escalationEnabled() != null)           p.setEscalationEnabled(req.escalationEnabled());
        if (req.escalationTargetUserId() != null)      p.setEscalationTargetUserId(req.escalationTargetUserId());

        if (req.voiceConsentGranted() != null) {
            if (req.voiceConsentGranted()) {
                // GRANT path — first-time or re-grant after revoke.
                if (p.getVoiceConsentGivenAt() == null) {
                    Instant now = Instant.now();
                    p.setVoiceConsentGivenAt(now);
                    p.setVoiceConsentTextVersion(CURRENT_CONSENT_VERSION);
                    p.setVoiceConsentIp(clientIp);
                    p.setVoiceConsentUserAgent(userAgent);
                    consentHistoryRepo.save(VoiceConsentHistory.builder()
                            .userId(userId)
                            .action(VoiceConsentHistory.Action.GRANTED)
                            .textVersion(CURRENT_CONSENT_VERSION)
                            .ip(clientIp)
                            .userAgent(userAgent)
                            .initiatedBy(VoiceConsentHistory.InitiatedBy.USER)
                            .build());
                    log.info("[Consent] GRANTED userId={} version={} ip={}",
                            userId, CURRENT_CONSENT_VERSION, clientIp);
                }
            } else {
                // REVOKE path — clear stamp, append history row, force
                // voice off (PDPL Điều 12: withdraw must be immediate).
                p.setVoiceConsentGivenAt(null);
                p.setVoiceEnabled(false);
                consentHistoryRepo.save(VoiceConsentHistory.builder()
                        .userId(userId)
                        .action(VoiceConsentHistory.Action.REVOKED)
                        .textVersion(p.getVoiceConsentTextVersion())
                        .ip(clientIp)
                        .userAgent(userAgent)
                        .initiatedBy(VoiceConsentHistory.InitiatedBy.USER)
                        .build());
                log.info("[Consent] REVOKED userId={} ip={}", userId, clientIp);
            }
        }

        return prefsRepo.save(p);
    }

    public NotificationPreferencesDto toDto(UserNotificationPreferences p) {
        return new NotificationPreferencesDto(
                p.getUserId(), p.getLanguage(),
                p.isEmailEnabled(), p.isPushEnabled(), p.isSmsEnabled(), p.isVoiceEnabled(),
                p.isQuietHoursEnabled(),
                p.getQuietHoursStart(), p.getQuietHoursEnd(), p.isQuietHoursOverrideCritical(),
                p.getVoiceMaxRetries(), p.getVoiceRetryIntervalSec(), p.getVoiceRateLimitSec(),
                p.getVoiceGender(), p.getVoiceSpeed(), p.isDtmfAckEnabled(),
                p.isEscalationEnabled(), p.getEscalationTargetUserId(),
                p.getVoiceConsentGivenAt()
        );
    }

    public static LocaleType mapProtoLanguageToLocale(String protoLang) {
        if (protoLang == null || protoLang.isBlank()) return LocaleType.vi_VN;
        return switch (protoLang.toLowerCase()) {
            case "en", "en_us", "en-us" -> LocaleType.en_US;
            case "ja", "ja_jp", "ja-jp" -> LocaleType.ja_JP;
            default -> LocaleType.vi_VN;
        };
    }
}
