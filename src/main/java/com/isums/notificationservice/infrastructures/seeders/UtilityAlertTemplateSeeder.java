package com.isums.notificationservice.infrastructures.seeders;

import com.isums.notificationservice.domains.entities.EmailTemplate;
import com.isums.notificationservice.domains.entities.EmailTemplateVersion;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateRepository;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class UtilityAlertTemplateSeeder {

    private static final String TEMPLATE_KEY    = "utility_threshold_exceeded";
    private static final String CATEGORY        = "ALERT";
    private static final String RECIPIENT_TYPE  = "LANDLORD";
    private static final String ACTOR           = "system";
    private static final List<String> ALLOWED_VARS = List.of(
            "landlordName", "userName", "houseName", "metricLabel", "currentUsage",
            "monthlyLimit", "unit", "usagePercent", "month", "severity", "occurredAt"
    );

    @Value("${app.seed.email-templates:true}")
    private boolean enabled;

    @Bean
    ApplicationRunner seedUtilityAlertTemplatesRunner(
            EmailTemplateRepository templateRepo,
            EmailTemplateVersionRepository versionRepo) {
        return args -> {
            if (!enabled) return;
            try {
                seed(templateRepo, versionRepo);
            } catch (Exception e) {

                log.error("[UtilityAlert] template seed failed: {}", e.getMessage(), e);
            }
        };
    }

    @Transactional
    public void seed(EmailTemplateRepository templateRepo, EmailTemplateVersionRepository versionRepo) {
        upsertIfAbsent(templateRepo, versionRepo,
                LocaleType.vi_VN,
                "Cảnh báo {{severity}}: {{houseName}} vượt ngưỡng {{metricLabel}} ({{usagePercent}}%)",
                htmlVi(),
                """
                        Xin chào {{landlordName}},

                        Nhà {{houseName}} đã vượt ngưỡng tiêu thụ {{metricLabel}} tháng {{month}}.
                        - Đã dùng:  {{currentUsage}} {{unit}}
                        - Hạn mức:  {{monthlyLimit}} {{unit}}
                        - Mức:      {{usagePercent}}%
                        - Mức độ:   {{severity}}

                        Hãy kiểm tra nhà hoặc liên hệ người thuê để xử lý.
                        """
        );
        upsertIfAbsent(templateRepo, versionRepo,
                LocaleType.en_US,
                "{{severity}}: {{houseName}} over {{metricLabel}} limit ({{usagePercent}}%)",
                htmlEn(),
                """
                        Hello {{landlordName}},

                        {{houseName}} has crossed its {{metricLabel}} consumption limit for {{month}}.
                        - Used:    {{currentUsage}} {{unit}}
                        - Limit:   {{monthlyLimit}} {{unit}}
                        - Ratio:   {{usagePercent}}%
                        - Level:   {{severity}}

                        Please check the property or contact the tenant.
                        """
        );
        upsertIfAbsent(templateRepo, versionRepo,
                LocaleType.ja_JP,
                "【{{severity}}】{{houseName}}の{{metricLabel}}使用量がしきい値を超過（{{usagePercent}}%）",
                htmlJa(),
                """
                        {{landlordName}}様、

                        {{month}}の{{houseName}}における{{metricLabel}}使用量がしきい値を超過しました。
                        - 使用量: {{currentUsage}} {{unit}}
                        - 上限:   {{monthlyLimit}} {{unit}}
                        - 割合:   {{usagePercent}}%
                        - 区分:   {{severity}}

                        物件をご確認いただくか、テナントにご連絡をお願いいたします。
                        """
        );
        seedDispatchAlias(templateRepo, versionRepo, "alert_utility_electricity_warning",
                "Cảnh báo điện: {{houseName}} đã dùng {{usagePercent}}% hạn mức",
                "Electricity warning: {{houseName}} used {{usagePercent}}% of its limit",
                "電力警告: {{houseName}}は上限の{{usagePercent}}%を使用");
        seedDispatchAlias(templateRepo, versionRepo, "alert_utility_water_warning",
                "Cảnh báo nước: {{houseName}} đã dùng {{usagePercent}}% hạn mức",
                "Water warning: {{houseName}} used {{usagePercent}}% of its limit",
                "水道警告: {{houseName}}は上限の{{usagePercent}}%を使用");
        seedDispatchAlias(templateRepo, versionRepo, "alert_utility_electricity_critical",
                "Khẩn cấp điện: {{houseName}} đã vượt hạn mức",
                "Critical electricity alert: {{houseName}} exceeded its limit",
                "緊急電力警報: {{houseName}}が上限を超過");
        seedDispatchAlias(templateRepo, versionRepo, "alert_utility_water_critical",
                "Khẩn cấp nước: {{houseName}} đã vượt hạn mức",
                "Critical water alert: {{houseName}} exceeded its limit",
                "緊急水道警報: {{houseName}}が上限を超過");
    }

    private void upsertIfAbsent(
            EmailTemplateRepository templateRepo,
            EmailTemplateVersionRepository versionRepo,
            LocaleType locale, String subject, String html, String text) {

        EmailTemplate tpl = templateRepo.findByTemplateKey(TEMPLATE_KEY)
                .orElseGet(() -> templateRepo.save(
                        EmailTemplate.builder()
                                .templateKey(TEMPLATE_KEY)
                                .category(CATEGORY)
                                .recipientType(RECIPIENT_TYPE)
                                .createdBy(ACTOR)
                                .updatedBy(ACTOR)
                                .build()));

        boolean hasActive = versionRepo
                .findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(
                        TEMPLATE_KEY, locale, TemplateStatus.ACTIVE
                ).isPresent();
        if (hasActive) return;

        EmailTemplateVersion v1 = EmailTemplateVersion.builder()
                .template(tpl)
                .locale(locale)
                .version(1)
                .status(TemplateStatus.ACTIVE)
                .subjectTpl(subject)
                .htmlTpl(html)
                .textTpl(text)
                .allowedVars(ALLOWED_VARS)
                .createdBy(ACTOR)
                .updatedBy(ACTOR)
                .build();
        versionRepo.save(v1);
        log.info("[UtilityAlert] seeded template {} locale={}", TEMPLATE_KEY, locale);
    }

    private void seedDispatchAlias(
            EmailTemplateRepository templateRepo,
            EmailTemplateVersionRepository versionRepo,
            String templateKey,
            String subjectVi,
            String subjectEn,
            String subjectJa) {
        upsertIfAbsent(templateRepo, versionRepo, templateKey, "TENANT", LocaleType.vi_VN,
                subjectVi, dispatchHtml("Xin chào {{userName}},", "Nhà {{houseName}} đang ở mức {{severity}} về {{metricLabel}} trong tháng {{month}}."),
                dispatchText("Xin chào {{userName}},", "Nhà {{houseName}} đang ở mức {{severity}} về {{metricLabel}} trong tháng {{month}}."));
        upsertIfAbsent(templateRepo, versionRepo, templateKey, "TENANT", LocaleType.en_US,
                subjectEn, dispatchHtml("Hello {{userName}},", "{{houseName}} is at {{severity}} level for {{metricLabel}} consumption in {{month}}."),
                dispatchText("Hello {{userName}},", "{{houseName}} is at {{severity}} level for {{metricLabel}} consumption in {{month}}."));
        upsertIfAbsent(templateRepo, versionRepo, templateKey, "TENANT", LocaleType.ja_JP,
                subjectJa, dispatchHtml("{{userName}}様、", "{{month}}の{{houseName}}における{{metricLabel}}使用量が{{severity}}状態です。"),
                dispatchText("{{userName}}様、", "{{month}}の{{houseName}}における{{metricLabel}}使用量が{{severity}}状態です。"));
    }

    private void upsertIfAbsent(
            EmailTemplateRepository templateRepo,
            EmailTemplateVersionRepository versionRepo,
            String templateKey, String recipientType,
            LocaleType locale, String subject, String html, String text) {

        EmailTemplate tpl = templateRepo.findByTemplateKey(templateKey)
                .orElseGet(() -> templateRepo.save(
                        EmailTemplate.builder()
                                .templateKey(templateKey)
                                .category(CATEGORY)
                                .recipientType(recipientType)
                                .createdBy(ACTOR)
                                .updatedBy(ACTOR)
                                .build()));

        boolean hasActive = versionRepo
                .findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(
                        templateKey, locale, TemplateStatus.ACTIVE
                ).isPresent();
        if (hasActive) return;

        EmailTemplateVersion v1 = EmailTemplateVersion.builder()
                .template(tpl)
                .locale(locale)
                .version(1)
                .status(TemplateStatus.ACTIVE)
                .subjectTpl(subject)
                .htmlTpl(html)
                .textTpl(text)
                .allowedVars(ALLOWED_VARS)
                .createdBy(ACTOR)
                .updatedBy(ACTOR)
                .build();
        versionRepo.save(v1);
        log.info("[UtilityAlert] seeded template {} locale={}", templateKey, locale);
    }

    private static String dispatchText(String greeting, String lead) {
        return """
                %s

                %s
                - Đã dùng / Used: {{currentUsage}} {{unit}}
                - Hạn mức / Limit: {{monthlyLimit}} {{unit}}
                - Tỷ lệ / Ratio: {{usagePercent}}%%
                - Thời điểm / Time: {{occurredAt}}
                """.formatted(greeting, lead);
    }

    private static String dispatchHtml(String greeting, String lead) {
        return """
                <!doctype html><html><head><meta charset="utf-8"></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="width:100%%;max-width:600px;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                        <tr><td style="padding:20px 24px;background:#0f766e;color:#fff;">
                          <div style="font-size:12px;opacity:.9;letter-spacing:.08em;">ISUMS UTILITY ALERT</div>
                          <div style="font-size:20px;font-weight:700;margin-top:4px;">{{houseName}} - {{usagePercent}}%%</div>
                        </td></tr>
                        <tr><td style="padding:22px 24px;color:#1f2937;">
                          <p style="margin:0 0 12px;font-size:15px;">%s</p>
                          <p style="margin:0 0 14px;font-size:14px;line-height:1.55;">%s</p>
                          <table cellpadding="0" cellspacing="0" style="width:100%%;border-collapse:collapse;border:1px solid #e5e7eb;">
                            <tr><td style="padding:9px 12px;color:#6b7280;">Used</td><td style="padding:9px 12px;"><strong>{{currentUsage}} {{unit}}</strong></td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:9px 12px;color:#6b7280;">Limit</td><td style="padding:9px 12px;">{{monthlyLimit}} {{unit}}</td></tr>
                            <tr><td style="padding:9px 12px;color:#6b7280;">Ratio</td><td style="padding:9px 12px;color:#dc2626;font-weight:700;">{{usagePercent}}%%</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:9px 12px;color:#6b7280;">Time</td><td style="padding:9px 12px;">{{occurredAt}}</td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(greeting, lead);
    }

    private static String htmlVi() {
        return """
                <!doctype html><html lang="vi"><head><meta charset="utf-8"></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
                  <table width="100%" cellpadding="0" cellspacing="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="width:100%;max-width:600px;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 14px rgba(0,0,0,0.06);">
                        <tr><td style="padding:24px 28px;background:linear-gradient(135deg,#f59e0b,#dc2626);color:#fff;">
                          <div style="font-size:12px;opacity:.9;letter-spacing:.12em;">CẢNH BÁO TIÊU THỤ TIỆN ÍCH</div>
                          <div style="font-size:22px;font-weight:700;margin-top:4px;">{{houseName}} — {{metricLabel}} {{usagePercent}}%</div>
                        </td></tr>
                        <tr><td style="padding:24px 28px;color:#1f2937;">
                          <p style="margin:0 0 14px;font-size:15px;">Xin chào <strong>{{landlordName}}</strong>,</p>
                          <p style="margin:0 0 14px;font-size:14px;line-height:1.55;">
                            Nhà <strong>{{houseName}}</strong> đang <strong>{{severity}}</strong> về tiêu thụ
                            <strong>{{metricLabel}}</strong> trong tháng <strong>{{month}}</strong>.
                            Vui lòng xem lại hoặc liên hệ người thuê để tránh phát sinh vượt hạn mức hợp đồng.
                          </p>
                          <table cellpadding="0" cellspacing="0" style="width:100%;border-collapse:collapse;margin:8px 0 18px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;width:45%;">Đã sử dụng</td><td style="padding:10px 14px;font-size:14px;"><strong>{{currentUsage}} {{unit}}</strong></td></tr>
                            <tr><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Hạn mức tháng</td><td style="padding:10px 14px;font-size:14px;">{{monthlyLimit}} {{unit}}</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Mức sử dụng</td><td style="padding:10px 14px;font-size:14px;color:#dc2626;font-weight:700;">{{usagePercent}}%</td></tr>
                            <tr><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Mức độ</td><td style="padding:10px 14px;font-size:14px;">{{severity}}</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Thời điểm</td><td style="padding:10px 14px;font-size:14px;">{{occurredAt}}</td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:14px 28px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <div style="font-size:11px;color:#9ca3af;">Email gửi tự động từ hệ thống ISUMS. Vui lòng đăng nhập vào dashboard để xem chi tiết và xử lý.</div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """;
    }

    private static String htmlEn() {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8"></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
                  <table width="100%" cellpadding="0" cellspacing="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="width:100%;max-width:600px;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 14px rgba(0,0,0,0.06);">
                        <tr><td style="padding:24px 28px;background:linear-gradient(135deg,#f59e0b,#dc2626);color:#fff;">
                          <div style="font-size:12px;opacity:.9;letter-spacing:.12em;">UTILITY THRESHOLD ALERT</div>
                          <div style="font-size:22px;font-weight:700;margin-top:4px;">{{houseName}} — {{metricLabel}} {{usagePercent}}%</div>
                        </td></tr>
                        <tr><td style="padding:24px 28px;color:#1f2937;">
                          <p style="margin:0 0 14px;font-size:15px;">Hello <strong>{{landlordName}}</strong>,</p>
                          <p style="margin:0 0 14px;font-size:14px;line-height:1.55;">
                            <strong>{{houseName}}</strong> is in <strong>{{severity}}</strong> state for
                            <strong>{{metricLabel}}</strong> consumption in <strong>{{month}}</strong>.
                            Please review or reach out to the tenant before the monthly cap is breached.
                          </p>
                          <table cellpadding="0" cellspacing="0" style="width:100%;border-collapse:collapse;margin:8px 0 18px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;width:45%;">Used</td><td style="padding:10px 14px;font-size:14px;"><strong>{{currentUsage}} {{unit}}</strong></td></tr>
                            <tr><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Monthly limit</td><td style="padding:10px 14px;font-size:14px;">{{monthlyLimit}} {{unit}}</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Usage ratio</td><td style="padding:10px 14px;font-size:14px;color:#dc2626;font-weight:700;">{{usagePercent}}%</td></tr>
                            <tr><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Level</td><td style="padding:10px 14px;font-size:14px;">{{severity}}</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;">Occurred at</td><td style="padding:10px 14px;font-size:14px;">{{occurredAt}}</td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:14px 28px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <div style="font-size:11px;color:#9ca3af;">Sent automatically by ISUMS. Sign in to the dashboard for full detail and actions.</div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """;
    }

    private static String htmlJa() {
        return """
                <!doctype html><html lang="ja"><head><meta charset="utf-8"></head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
                  <table width="100%" cellpadding="0" cellspacing="0" style="padding:24px 12px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="width:100%;max-width:600px;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 14px rgba(0,0,0,0.06);">
                        <tr><td style="padding:24px 28px;background:linear-gradient(135deg,#f59e0b,#dc2626);color:#fff;">
                          <div style="font-size:12px;opacity:.9;letter-spacing:.12em;">ユーティリティ警報</div>
                          <div style="font-size:22px;font-weight:700;margin-top:4px;">{{houseName}} — {{metricLabel}} {{usagePercent}}%</div>
                        </td></tr>
                        <tr><td style="padding:24px 28px;color:#1f2937;">
                          <p style="margin:0 0 14px;font-size:15px;"><strong>{{landlordName}}</strong>様、</p>
                          <p style="margin:0 0 14px;font-size:14px;line-height:1.55;">
                            <strong>{{month}}</strong>の<strong>{{houseName}}</strong>における
                            <strong>{{metricLabel}}</strong>使用量が<strong>{{severity}}</strong>状態です。
                            契約上限を超過する前に、物件の確認またはテナントへの連絡をお願いいたします。
                          </p>
                          <table cellpadding="0" cellspacing="0" style="width:100%;border-collapse:collapse;margin:8px 0 18px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;width:45%;">使用量</td><td style="padding:10px 14px;font-size:14px;"><strong>{{currentUsage}} {{unit}}</strong></td></tr>
                            <tr><td style="padding:10px 14px;font-size:12px;color:#6b7280;">月間上限</td><td style="padding:10px 14px;font-size:14px;">{{monthlyLimit}} {{unit}}</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;">使用率</td><td style="padding:10px 14px;font-size:14px;color:#dc2626;font-weight:700;">{{usagePercent}}%</td></tr>
                            <tr><td style="padding:10px 14px;font-size:12px;color:#6b7280;">区分</td><td style="padding:10px 14px;font-size:14px;">{{severity}}</td></tr>
                            <tr style="background:#f9fafb;"><td style="padding:10px 14px;font-size:12px;color:#6b7280;">検出時刻</td><td style="padding:10px 14px;font-size:14px;">{{occurredAt}}</td></tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:14px 28px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <div style="font-size:11px;color:#9ca3af;">ISUMSより自動送信。詳細はダッシュボードにログインしてご確認ください。</div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """;
    }
}
