package com.isums.notificationservice.infrastructures.abstracts;

import com.isums.notificationservice.domains.enums.LocaleType;

import java.util.Map;

public interface EmailService {
    public void sendEmail(String to, String templateKey, LocaleType locale, Map<String, Object> placeHolders);
}
