package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.dtos.ConfirmAndSendToTenantEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.grpc.UserResponse;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EContractEventListener {

    private final EmailService emailService;
    private final UserGrpcClient userGrpcClient;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @KafkaListener(topics = "confirmAndSendToTenant-topic", groupId = "notification-group")
    public void HandleEContractEventSendToTenant(ConfirmAndSendToTenantEvent event) {
        UUID tenantId = event.getTenantId();
        UserResponse user = userGrpcClient.getUserById(tenantId);
        if (user == null) {
            log.error("User not found: {}", tenantId);
            return;
        }

        Map<String, Object> vars = new HashMap<>();
        String tenantName = safeFirstNonBlank(user.getName(), "bạn");
        vars.put("tenantName", tenantName);

        vars.put("landlordName", safe("event.getLandlordName()", "Chủ nhà"));
        vars.put("contractNo", safe("event.getContractNo()", "N/A"));
        vars.put("contractName", safe("event.getContractName()", "Hợp đồng thuê nhà"));
        vars.put("propertyAddress", safe("event.getPropertyAddress()", "N/A"));

        vars.put("startDate", formatDate(Instant.now()));
        vars.put("endDate", formatDate(Instant.now()));

        // 4) URLs
        vars.put("viewUrl", safe(event.getUrl(), "#"));
        vars.put("confirmUrl", safe(event.getUrl(), "#"));

        // 5) expiry display
        vars.put("expiresIn", safe("24 giờ", "24 giờ"));
        String emailKey = "econtract_view_confirm";
        try {
            emailService.sendEmail(user.getEmail(), emailKey, LocaleType.vi_VN, vars);
            log.info("Email sent to={}", user.getEmail());
        } catch (Exception e) {
            log.error("Error while sending email to={}", user.getEmail(), e);
        }
    }

    private String safe(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        return t.isEmpty() ? fallback : t;
    }

    private String safeFirstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) return c.trim();
        }
        return "";
    }

    private String formatDate(Instant instant) {
        if (instant == null) return "N/A";
        return DMY.format(instant);
    }
}
