package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.enums.AlertSeverity;
import com.isums.notificationservice.domains.enums.RecipientRole;

/**
 * The production channel-routing matrix. Driven by (severity × role) —
 * tenant gets the loudest treatment for safety events, landlord gets
 * SMS for non-critical, manager defaults to digest-only to avoid
 * fatigue at scale.
 *
 * <p>Channel flags are PERMISSION GRANTS — actual delivery still
 * respects user preferences (consent, opt-out, quiet hours) and
 * subscription tier. The policy can never RAISE delivery beyond a
 * user's settings; it can only SUPPRESS.
 *
 * <p>For non-tenant roles (landlord / manager), tier checks are skipped
 * because they're a business cost of the property owner, not a
 * subscription product. The landlord's own tier still gates calls
 * placed TO the landlord's number, but voice/SMS to the landlord on a
 * tenant alert isn't tier-gated.
 */
public record ChannelPolicy(
        boolean push,
        boolean email,
        boolean sms,
        boolean voice
) {
    public static ChannelPolicy forSeverityRole(AlertSeverity severity, RecipientRole role) {
        return switch (severity) {
            case CRITICAL -> switch (role) {
                // Tenant: first responder — every channel
                case TENANT   -> new ChannelPolicy(true, true, true, true);
                // Landlord: EMAIL ONLY. Landlord pays the manager to be
                // on-call; bothering them at 2am for sensor blips burns
                // the relationship. They get visibility via email.
                case LANDLORD -> new ChannelPolicy(false, true, false, false);
                // Manager: full ops contact — voice + SMS + push + email.
                // This is who actually drives to the site.
                case MANAGER  -> new ChannelPolicy(true, true, true, true);
            };
            case WARNING -> switch (role) {
                // Tenant: in-app push + email only — no SMS / voice cost on warnings
                case TENANT   -> new ChannelPolicy(true, true, false, false);
                // Landlord: email only (no SMS noise)
                case LANDLORD -> new ChannelPolicy(false, true, false, false);
                // Manager: in-app push + email only — SMS/voice reserved for CRITICAL
                case MANAGER  -> new ChannelPolicy(true, true, false, false);
            };
            case INFO -> switch (role) {
                // Tenant: lightweight push + email
                case TENANT   -> new ChannelPolicy(true, true, false, false);
                // Landlord: skip entirely — INFO doesn't need owner attention
                case LANDLORD -> new ChannelPolicy(false, false, false, false);
                // Manager: email only (operational signal for daily review)
                case MANAGER  -> new ChannelPolicy(false, true, false, false);
            };
        };
    }
}
