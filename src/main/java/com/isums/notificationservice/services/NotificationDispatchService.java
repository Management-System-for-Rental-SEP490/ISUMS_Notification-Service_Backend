package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.AlertDispatchRequest;
import com.isums.notificationservice.domains.dtos.AlertDispatchResponse;
import com.isums.notificationservice.domains.dtos.AlertDispatchResponse.ChannelDispatchResult;
import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.domains.enums.AlertSeverity;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.NotificationChannel;
import com.isums.notificationservice.domains.enums.RecipientRole;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.abstracts.SmsProvider;
import com.isums.notificationservice.infrastructures.grpcs.HouseGrpcClient;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Production routing entrypoint. One alert fans out to up to three
 * recipients — tenant + landlord + manager — using a (severity × role)
 * channel matrix ({@link ChannelPolicy}). Each recipient gets their
 * own delivery in their OWN locale (vi/en/ja from their User profile).
 *
 * <p>Hard rules (defence-in-depth):
 * <ul>
 *   <li>User preferences are a HARD CEILING — policy can suppress but
 *       never raise beyond what the user has opted in to.</li>
 *   <li>Tenant pays for voice/SMS via PREMIUM tier; landlord and
 *       manager are business recipients on the property owner's bill,
 *       no tier check.</li>
 *   <li>Quiet hours bypass only for CRITICAL events when the user has
 *       opted into the override (default true).</li>
 *   <li>Per-recipient rate limit + monthly quota — landlord receiving
 *       voice calls for 5 different houses still hits their own limit
 *       independently.</li>
 * </ul>
 *
 * <p>Best-effort: a single-channel failure never blocks the rest. Every
 * outcome lands in the response so Lambda can log a summary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationPreferenceService preferenceService;
    private final NotificationQuotaService quotaService;
    private final VoiceCallOrchestratorService voiceOrchestrator;
    private final EmailService emailService;
    private final VoiceProviderRouter providerRouter;
    private final SmsProvider smsProvider;
    private final UserGrpcClient userGrpcClient;
    private final HouseGrpcClient houseGrpcClient;
    private final ChannelTemplateRenderer templateRenderer;

    /**
     * Globally disables the SMS path. Trial Stringee accounts ship without
     * any registered brandname — every {@code POST /v1/sms} returns
     * {@code "From number invalid"}. Flip to {@code true} (the default
     * for production) once you've registered a brandname with the
     * carrier through Stringee Console.
     */
    @org.springframework.beans.factory.annotation.Value("${app.notification.sms.enabled:false}")
    private boolean smsGloballyEnabled;

    public AlertDispatchResponse dispatch(AlertDispatchRequest req) {
        List<ChannelDispatchResult> results = new ArrayList<>();
        AlertSeverity severity = req.eventType().severity();

        // Test calls flagged via templateVars["testMode"]=true. The flag
        // means "tenant is verifying their own setup" — fanning out to
        // landlord/manager would spam business contacts every time the
        // tenant tries the in-app test button. Real IoT alerts (gas, fire)
        // still broadcast normally; only the self-test path is restricted.
        boolean testMode = req.templateVars() != null
                && Boolean.TRUE.equals(req.templateVars().get("testMode"));

        log.info("[Dispatch] alertId={} eventType={} severity={} tenant={} houseId={} testMode={}",
                req.alertId(), req.eventType(), severity, req.userId(), req.houseId(), testMode);

        // -- 1. Tenant (primary recipient) --
        dispatchToRecipient(req, req.userId(), RecipientRole.TENANT, severity, results);

        // -- 2. Landlord + manager (only if non-INFO and houseId resolvable) --
        // Skipped entirely in test mode — manager is reached only when the
        // tenant explicitly presses DTMF=2 (escalation flow in
        // VoiceWebhookHandler.applyDtmf), not on the initial test ping.
        if (!testMode && severity != AlertSeverity.INFO && nonBlank(req.houseId())) {
            UUID houseUuid;
            try {
                houseUuid = UUID.fromString(req.houseId());
            } catch (IllegalArgumentException e) {
                log.warn("[Dispatch] invalid houseId={} — skipping landlord/manager fan-out",
                        req.houseId());
                return new AlertDispatchResponse(true, req.userId(), results);
            }

            UUID landlordId = lookupSafely(() -> houseGrpcClient.getLandlordIdByHouseId(houseUuid),
                    "landlord", req.houseId());
            UUID managerId = lookupSafely(() -> houseGrpcClient.getManagerIdByHouseId(houseUuid),
                    "manager", req.houseId());

            if (landlordId != null && !landlordId.equals(req.userId())) {
                dispatchToRecipient(req, landlordId, RecipientRole.LANDLORD, severity, results);
            }
            if (managerId != null
                    && !managerId.equals(req.userId())
                    && !managerId.equals(landlordId)) {
                dispatchToRecipient(req, managerId, RecipientRole.MANAGER, severity, results);
            }
        }

        return new AlertDispatchResponse(true, req.userId(), results);
    }

    /**
     * Direct dispatch to a specific recipient — used by the webhook
     * handler for escalation re-dispatch (DTMF=2 or NO_ANSWER_MAX_RETRIES).
     * Skips the fan-out so we don't recurse the original tenant.
     * Backward-compat overload — pass null reason.
     */
    public AlertDispatchResponse dispatchDirect(AlertDispatchRequest req,
                                                  UUID targetUserId,
                                                  RecipientRole targetRole) {
        return dispatchDirect(req, targetUserId, targetRole, null);
    }

    /**
     * Same as the no-reason overload but lets the caller propagate the
     * {@code EscalationReason} so the voice template picker can choose
     * a manager script that matches reality (DTMF forwarded vs
     * tenant-didn't-answer auto-escalation).
     */
    public AlertDispatchResponse dispatchDirect(AlertDispatchRequest req,
                                                  UUID targetUserId,
                                                  RecipientRole targetRole,
                                                  com.isums.notificationservice.domains.enums.EscalationReason reason) {
        List<ChannelDispatchResult> results = new ArrayList<>();
        AlertSeverity severity = req.eventType().severity();
        log.info("[Dispatch] DIRECT escalation to userId={} role={} reason={} alertId={}",
                targetUserId, targetRole, reason, req.alertId());
        dispatchToRecipient(req, targetUserId, targetRole, severity, results, reason);
        return new AlertDispatchResponse(true, targetUserId, results);
    }

    // ─── Per-recipient delivery ────────────────────────────────────────

    private void dispatchToRecipient(AlertDispatchRequest req,
                                       UUID userId,
                                       RecipientRole role,
                                       AlertSeverity severity,
                                       List<ChannelDispatchResult> results) {
        dispatchToRecipient(req, userId, role, severity, results, null);
    }

    private void dispatchToRecipient(AlertDispatchRequest req,
                                       UUID userId,
                                       RecipientRole role,
                                       AlertSeverity severity,
                                       List<ChannelDispatchResult> results,
                                       com.isums.notificationservice.domains.enums.EscalationReason reason) {

        String prefix = role.name() + "/";

        // ID semantics across the dispatch graph:
        //   - TENANT  : userId normally arrives from JWT.sub → Keycloak UUID.
        //               Some IoT Lambdas still pass the internal users.id from
        //               esp32_asset_map; resolveTenantUser handles both.
        //   - LANDLORD/MANAGER : userId arrives from house-grpc landlord/manager
        //                       resolution → INTERNAL users.id UUID.
        // We branch the gRPC lookup so each side uses the right rpc, then
        // canonicalise downstream lookups (prefs / sub) on the Keycloak ID
        // returned in the response — Notification-Service's tables are
        // keyed by JWT.sub, so a mismatch here would surface as "user not
        // found" / always-FREE tier (which is exactly the bug we hit).
        UserResponse user;
        try {
            user = (role == RecipientRole.TENANT)
                    ? resolveTenantUser(userId)
                    : userGrpcClient.getUserById(userId);
        } catch (Exception e) {
            log.error("[Dispatch] user lookup failed userId={} role={}: {}",
                    userId, role, e.getMessage());
            results.add(new ChannelDispatchResult(prefix + "ALL", "FAILED", "user_lookup_failed", null));
            return;
        }

        // Canonicalise to Keycloak ID for prefs / subscription lookup,
        // regardless of how the caller passed the user in.
        UUID keycloakUuid;
        try {
            keycloakUuid = UUID.fromString(user.getKeycloakId());
        } catch (Exception e) {
            log.error("[Dispatch] user has malformed keycloakId={} role={}",
                    user.getKeycloakId(), role);
            results.add(new ChannelDispatchResult(prefix + "ALL", "FAILED", "bad_keycloak_id", null));
            return;
        }

        UserNotificationPreferences prefs = preferenceService.getOrCreate(keycloakUuid);
        NotificationSubscription sub;
        if (nonBlank(req.houseId())) {
            try {
                UUID houseUuid = UUID.fromString(req.houseId());
                sub = preferenceService.getSubscriptionOrCreate(keycloakUuid, houseUuid);
            } catch (IllegalArgumentException e) {
                log.warn("[Dispatch] invalid houseId={} for subscription lookup, using FREE tier", req.houseId());
                sub = NotificationSubscription.builder()
                        .userId(keycloakUuid)
                        .tier(com.isums.notificationservice.domains.enums.SubscriptionTier.FREE)
                        .voiceQuotaMonthly(TierQuotaPolicy.voiceQuotaFor(com.isums.notificationservice.domains.enums.SubscriptionTier.FREE))
                        .smsQuotaMonthly(TierQuotaPolicy.smsQuotaFor(com.isums.notificationservice.domains.enums.SubscriptionTier.FREE))
                        .build();
            }
        } else {
            sub = NotificationSubscription.builder()
                    .userId(keycloakUuid)
                    .tier(com.isums.notificationservice.domains.enums.SubscriptionTier.FREE)
                    .voiceQuotaMonthly(TierQuotaPolicy.voiceQuotaFor(com.isums.notificationservice.domains.enums.SubscriptionTier.FREE))
                    .smsQuotaMonthly(TierQuotaPolicy.smsQuotaFor(com.isums.notificationservice.domains.enums.SubscriptionTier.FREE))
                    .build();
        }

        // Locale resolution: User-Service profile language is the system-wide
        // source of truth. Notification prefs used to carry a separate language,
        // but the web UI no longer edits it; treating the stale/default prefs
        // row as authoritative makes notifications ignore /users/me.language.
        LocaleType locale = resolveLocale(prefs, user);
        Map<String, Object> vars = buildTemplateVars(req, user);

        ChannelPolicy policy = ChannelPolicy.forSeverityRole(severity, role);
        log.info("[Dispatch] {} userId={} locale={} policy=[email={},push={},sms={},voice={}]",
                prefix, userId, locale,
                policy.email(), policy.push(), policy.sms(), policy.voice());

        ChannelDispatchResult emailRes = deliverEmail(prefix, policy, prefs, user, req, vars, locale);
        ChannelDispatchResult pushRes = deliverPush(prefix, policy);
        ChannelDispatchResult smsRes = deliverSms(prefix, policy, prefs, sub, user, req, vars, locale, role);
        ChannelDispatchResult voiceRes = policy.voice()
                ? deliverVoice(prefix, prefs, sub, user, req, vars, locale, role, reason)
                : new ChannelDispatchResult(prefix + "VOICE", "SKIPPED", "policy_off_for_role", null);

        results.add(emailRes);
        results.add(pushRes);
        results.add(smsRes);
        results.add(voiceRes);

        log.info("[Dispatch] {} outcome email={}({}) push={}({}) sms={}({}) voice={}({})",
                prefix,
                emailRes.status(), emailRes.reason(),
                pushRes.status(), pushRes.reason(),
                smsRes.status(), smsRes.reason(),
                voiceRes.status(), voiceRes.reason());
    }

    private UserResponse resolveTenantUser(UUID userId) {
        try {
            return userGrpcClient.getUserByKeycloakId(userId.toString());
        } catch (Exception keycloakLookupFailed) {
            log.warn("[Dispatch] tenant keycloak lookup failed userId={}, trying internal user id: {}",
                    userId, keycloakLookupFailed.getMessage());
            return userGrpcClient.getUserById(userId);
        }
    }

    private ChannelDispatchResult deliverEmail(String prefix, ChannelPolicy policy,
                                                  UserNotificationPreferences prefs,
                                                  UserResponse user,
                                                  AlertDispatchRequest req,
                                                  Map<String, Object> vars,
                                                  LocaleType locale) {
        if (!policy.email()) {
            return new ChannelDispatchResult(prefix + "EMAIL", "SKIPPED", "policy_off_for_role", null);
        }
        if (!prefs.isEmailEnabled()) {
            return new ChannelDispatchResult(prefix + "EMAIL", "SKIPPED", "user_disabled", null);
        }
        if (!nonBlank(user.getEmail())) {
            return new ChannelDispatchResult(prefix + "EMAIL", "SKIPPED", "no_email", null);
        }
        try {
            String emailTemplateKey = "alert_" + req.eventType().name().toLowerCase();
            emailService.sendEmail(user.getEmail(), emailTemplateKey, locale, vars);
            return new ChannelDispatchResult(prefix + "EMAIL", "SENT", null, null);
        } catch (Exception e) {
            log.warn("[Dispatch] {}email send failed: {}", prefix, e.getMessage());
            return new ChannelDispatchResult(prefix + "EMAIL", "SKIPPED", e.getMessage(), null);
        }
    }

    private ChannelDispatchResult deliverPush(String prefix, ChannelPolicy policy) {
        if (!policy.push()) {
            return new ChannelDispatchResult(prefix + "PUSH", "SKIPPED", "policy_off_for_role", null);
        }
        // The IoT Lambda tier already invokes ws-broadcaster on alert ingest;
        // duplicating here would double-send. Reported as SKIPPED for clarity
        // — the user STILL gets the in-app push, just from upstream.
        return new ChannelDispatchResult(prefix + "PUSH", "SKIPPED",
                "handled_by_ws_broadcaster_lambda", null);
    }

    private ChannelDispatchResult deliverSms(String prefix, ChannelPolicy policy,
                                                UserNotificationPreferences prefs,
                                                NotificationSubscription sub,
                                                UserResponse user,
                                                AlertDispatchRequest req,
                                                Map<String, Object> vars,
                                                LocaleType locale,
                                                RecipientRole role) {
        if (!smsGloballyEnabled) {
            return new ChannelDispatchResult(prefix + "SMS", "SKIPPED",
                    "sms_provider_unconfigured (no brandname)", null);
        }
        if (!policy.sms()) {
            return new ChannelDispatchResult(prefix + "SMS", "SKIPPED", "policy_off_for_role", null);
        }
        if (!prefs.isSmsEnabled()) {
            return new ChannelDispatchResult(prefix + "SMS", "SKIPPED", "user_disabled", null);
        }
        // Tenant pays via PREMIUM tier; landlord/manager are business recipients.
        if (role == RecipientRole.TENANT && sub.getTier() != SubscriptionTier.PREMIUM) {
            return new ChannelDispatchResult(prefix + "SMS", "SKIPPED", "tier_free", null);
        }
        if (!nonBlank(user.getPhoneNumber())) {
            return new ChannelDispatchResult(prefix + "SMS", "SKIPPED", "no_phone", null);
        }
        try {
            var rendered = templateRenderer.render(
                    "sms_" + req.eventType().name().toLowerCase(),
                    NotificationChannel.SMS, locale, vars);
            var resp = smsProvider.sendSms(user.getPhoneNumber(), rendered.body());
            if (!resp.ok()) {
                return new ChannelDispatchResult(prefix + "SMS", "FAILED",
                        resp.errorMessage(), null);
            }
            return new ChannelDispatchResult(prefix + "SMS", "SENT", null, null);
        } catch (Exception e) {
            log.warn("[Dispatch] {}sms send failed: {}", prefix, e.getMessage());
            return new ChannelDispatchResult(prefix + "SMS", "SKIPPED", e.getMessage(), null);
        }
    }

    private ChannelDispatchResult deliverVoice(String prefix,
                                                  UserNotificationPreferences prefs,
                                                  NotificationSubscription sub,
                                                  UserResponse user,
                                                  AlertDispatchRequest req,
                                                  Map<String, Object> vars,
                                                  LocaleType locale,
                                                  RecipientRole role,
                                                  com.isums.notificationservice.domains.enums.EscalationReason reason) {
        // Quota / rate-limit / orchestrator job rows all key off the same
        // identifier the prefs + subscription tables use → Keycloak ID.
        // Using `user.getId()` (internal users.id) here would build Redis
        // keys / look up subscription rows under a UUID that the rest of
        // Notification-Service never writes to → false "quota exceeded"
        // and orphaned voice_call_jobs.
        UUID keycloakUuid = UUID.fromString(user.getKeycloakId());
        boolean criticalSafety = req.eventType().severity() == AlertSeverity.CRITICAL;

        if (!prefs.isVoiceEnabled() && !criticalSafety) {
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED", "user_disabled", null);
        }
        if (role == RecipientRole.TENANT
                && sub.getTier() != SubscriptionTier.PREMIUM
                && !criticalSafety) {
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED", "tier_free", null);
        }
        if (role == RecipientRole.TENANT
                && prefs.getVoiceConsentGivenAt() == null
                && !criticalSafety) {
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED", "no_consent", null);
        }
        if (!nonBlank(user.getPhoneNumber())) {
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED", "no_phone", null);
        }
        if (QuietHoursPolicy.shouldSuppress(prefs, req.eventType()) && !criticalSafety) {
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED", "quiet_hours", null);
        }
        if (!quotaService.tryAcquireVoiceRateLimit(keycloakUuid, prefs.getVoiceRateLimitSec())) {
            long remaining = quotaService.remainingRateLimitSec(keycloakUuid);
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED",
                    "rate_limited_remaining_sec=" + remaining, null);
        }
        // Tier-quota only applies to TENANT (the paying customer). MANAGER /
        // LANDLORD are operations roles — landlord pays for the service so
        // their on-call staff always get voice. Without this bypass a fresh
        // manager subscription row (tier=FREE, quota=0) silently swallows
        // every escalation call → user complaint "manager's phone never
        // rings". Tenant path still gates on quota above the tier check.
        UUID dispatchHouseUuid = null;
        if (nonBlank(req.houseId())) {
            try {
                dispatchHouseUuid = UUID.fromString(req.houseId());
            } catch (IllegalArgumentException ignored) {
                dispatchHouseUuid = null;
            }
        }
        if (role == RecipientRole.TENANT
                && !quotaService.tryConsumeVoiceQuota(keycloakUuid, dispatchHouseUuid)) {
            return new ChannelDispatchResult(prefix + "VOICE", "SKIPPED",
                    "monthly_quota_exceeded", null);
        }

        try {
            UserNotificationPreferences effectivePrefs = prefs.getLanguage() == locale
                    ? prefs
                    : cloneWithLocale(prefs, locale);

            VoiceCallJob job = voiceOrchestrator.enqueueFirstAttempt(
                    keycloakUuid, user.getPhoneNumber(), effectivePrefs, req, vars, role, reason);
            return new ChannelDispatchResult(prefix + "VOICE", "SENT", null, job.getId());
        } catch (Exception e) {
            if (role == RecipientRole.TENANT) {
                quotaService.refundVoiceQuota(keycloakUuid, dispatchHouseUuid);
            }
            log.error("[Dispatch] {}voice dispatch failed userId={}: {}",
                    prefix, keycloakUuid, e.getMessage(), e);
            return new ChannelDispatchResult(prefix + "VOICE", "FAILED", e.getMessage(), null);
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private static LocaleType resolveLocale(UserNotificationPreferences prefs, UserResponse user) {
        if (user != null && user.getLanguage() != null && !user.getLanguage().isBlank()) {
            return NotificationPreferenceService.mapProtoLanguageToLocale(user.getLanguage());
        }
        if (prefs != null && prefs.getLanguage() != null) {
            return prefs.getLanguage();
        }
        return LocaleType.vi_VN;
    }

    private interface UuidLookup {
        UUID call();
    }

    private UUID lookupSafely(UuidLookup lookup, String label, String houseId) {
        try {
            return lookup.call();
        } catch (Exception e) {
            log.warn("[Dispatch] {} lookup failed houseId={}: {}", label, houseId, e.getMessage());
            return null;
        }
    }

    private static UserNotificationPreferences cloneWithLocale(
            UserNotificationPreferences p, LocaleType locale) {
        return UserNotificationPreferences.builder()
                .userId(p.getUserId())
                .language(locale)
                .emailEnabled(p.isEmailEnabled())
                .pushEnabled(p.isPushEnabled())
                .smsEnabled(p.isSmsEnabled())
                .voiceEnabled(p.isVoiceEnabled())
                .quietHoursStart(p.getQuietHoursStart())
                .quietHoursEnd(p.getQuietHoursEnd())
                .quietHoursOverrideCritical(p.isQuietHoursOverrideCritical())
                .voiceMaxRetries(p.getVoiceMaxRetries())
                .voiceRetryIntervalSec(p.getVoiceRetryIntervalSec())
                .voiceRateLimitSec(p.getVoiceRateLimitSec())
                .voiceGender(p.getVoiceGender())
                .voiceSpeed(p.getVoiceSpeed())
                .dtmfAckEnabled(p.isDtmfAckEnabled())
                .escalationEnabled(p.isEscalationEnabled())
                .escalationTargetUserId(p.getEscalationTargetUserId())
                .voiceConsentGivenAt(p.getVoiceConsentGivenAt())
                .build();
    }

    private Map<String, Object> buildTemplateVars(AlertDispatchRequest req, UserResponse user) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName",   user.getName() == null ? "" : user.getName());
        vars.put("houseId",    nz(req.houseId()));
        // Human-friendly house name — both tenants and managers can have
        // ties to multiple houses, so an alert that says only "tại Phòng
        // khách" is ambiguous. Resolved via house-grpc on every dispatch
        // (cheap call, ~5ms; House-Service caches at the entity layer).
        // Falls back to empty string when houseId is unset (test calls
        // before mainHouseId was wired) so the template renders cleanly.
        vars.put("houseName",  nonBlank(req.houseId())
                ? safeHouseName(req.houseId()) : "");
        vars.put("areaId",     nz(req.areaId()));
        vars.put("areaName",   nz(req.areaName()));
        vars.put("thing",      nz(req.thing()));
        vars.put("metric",     nz(req.metric()));
        vars.put("value",      req.value() == null ? "" : String.format("%.1f", req.value()));
        vars.put("unit",       nz(req.unit()));
        vars.put("eventType",  req.eventType().name());
        vars.put("occurredAt", Instant.now().toString());
        if (req.templateVars() != null) {
            vars.putAll(req.templateVars());
        }
        return vars;
    }

    /** Best-effort house name lookup — never throws to the dispatch path. */
    private String safeHouseName(String houseIdStr) {
        try {
            return houseGrpcClient.getHouseNameByHouseId(UUID.fromString(houseIdStr));
        } catch (Exception e) {
            log.warn("[Dispatch] house-name lookup failed houseId={}: {}",
                    houseIdStr, e.getMessage());
            return "";
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
