package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EContractEventListener {

    private final EmailService emailService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("\"recipientEmail\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"recipientName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONFIRM_URL_PATTERN = Pattern.compile("\"confirmUrl\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONTRACT_NAME_PATTERN = Pattern.compile("\"contractName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONTRACT_ID_PATTERN = Pattern.compile("\"contractId\"\\s*:\\s*\"([^\"]+)\"");

    @KafkaListener(topics = "confirmAndSendToTenant-topic", groupId = "notification-group")
    public void handleConfirmAndSendToTenant(String payload) {
        log.error("[EContract] >>> ENTRY len={}", payload != null ? payload.length() : -1);
        try {
            if (payload == null) {
                log.error("[EContract] null payload");
                return;
            }
            String email = extract(payload, EMAIL_PATTERN);
            String name = extract(payload, NAME_PATTERN);
            String url = extract(payload, URL_PATTERN);
            String confirmUrl = extract(payload, CONFIRM_URL_PATTERN);
            String contractName = extract(payload, CONTRACT_NAME_PATTERN);
            String contractId = extract(payload, CONTRACT_ID_PATTERN);

            log.error("[EContract] >>> PARSED email={} contractId={}", email, contractId);

            if (email == null || email.isBlank()) {
                log.error("[EContract] no recipientEmail in payload");
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", name != null ? name : "bạn");
            vars.put("landlordName", "Chủ nhà");
            vars.put("contractNo", contractId != null && contractId.length() >= 8
                    ? contractId.substring(0, 8).toUpperCase() : "N/A");
            vars.put("contractName", contractName != null ? contractName : "Hợp đồng thuê nhà");
            vars.put("propertyAddress", "N/A");
            vars.put("startDate", "N/A");
            vars.put("endDate", "N/A");
            vars.put("viewUrl", url != null ? url : "#");
            vars.put("confirmUrl", confirmUrl != null ? confirmUrl : (url != null ? url : "#"));
            vars.put("expiresIn", "24 giờ");

            emailService.sendEmail(email, "econtract_view_confirm", LocaleType.vi_VN, vars);
            log.error("[EContract] >>> EMAIL SENT to={}", email);
        } catch (Throwable t) {
            log.error("[EContract] >>> FAILED: {}", t.toString(), t);
        }
    }

    private String extract(String payload, Pattern p) {
        Matcher m = p.matcher(payload);
        return m.find() ? m.group(1) : null;
    }
}
