package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.enums.AlertEventType;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class QuietHoursPolicy {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private QuietHoursPolicy() {}

    /**
     * Returns true if voice call should be SUPPRESSED right now because of
     * quiet hours. CRITICAL events override when the user opted in (default).
     */
    public static boolean shouldSuppress(UserNotificationPreferences prefs, AlertEventType event) {
        // Master switch — when off, time-of-day window doesn't apply.
        if (!prefs.isQuietHoursEnabled()) return false;

        LocalTime start = prefs.getQuietHoursStart();
        LocalTime end   = prefs.getQuietHoursEnd();
        LocalTime now   = ZonedDateTime.now(VN).toLocalTime();

        boolean inWindow = inWindow(now, start, end);
        if (!inWindow) return false;

        if (event != null && event.isCritical() && prefs.isQuietHoursOverrideCritical()) {
            return false;
        }
        return true;
    }

    private static boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) return false; // zero-width window = always off
        if (start.isBefore(end)) {
            // Same-day window (e.g. 13:00 → 15:00)
            return !now.isBefore(start) && now.isBefore(end);
        }
        // Wraps midnight (e.g. 22:00 → 06:00)
        return !now.isBefore(start) || now.isBefore(end);
    }
}
