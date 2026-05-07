package com.isums.notificationservice.infrastructures.seeders;

import com.isums.notificationservice.domains.entities.ChannelTemplate;
import com.isums.notificationservice.domains.entities.ChannelTemplateVersion;
import com.isums.notificationservice.domains.enums.AlertEventType;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.NotificationChannel;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import com.isums.notificationservice.infrastructures.repositories.ChannelTemplateRepository;
import com.isums.notificationservice.infrastructures.repositories.ChannelTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class VoiceAlertTemplateSeeder {

    private static final String ACTOR = "system";
    private static final List<String> ALLOWED_VARS = List.of(
            "userName", "houseId", "areaId", "areaName", "thing",
            "metric", "metricLabel", "value", "unit", "eventType", "occurredAt", "level",
            "houseName", "currentUsage", "monthlyLimit", "usagePercent", "month", "severity"
    );

    @Value("${app.seed.voice-templates:true}")
    private boolean enabled;

    @Bean
    ApplicationRunner seedVoiceAlertTemplatesRunner(
            ChannelTemplateRepository templateRepo,
            ChannelTemplateVersionRepository versionRepo) {
        return args -> {
            if (!enabled) return;
            try {
                seedAll(templateRepo, versionRepo);
            } catch (Exception e) {
                log.error("[VoiceAlertSeed] failed: {}", e.getMessage(), e);
            }
        };
    }

    @Transactional
    public void seedAll(ChannelTemplateRepository templateRepo,
                          ChannelTemplateVersionRepository versionRepo) {
        seedVoice(templateRepo, versionRepo, AlertEventType.GAS_CRITICAL,
                "Cảnh báo khẩn cấp. Phát hiện khí gas vượt ngưỡng nguy hiểm tại {{areaName}}, "
                        + "{{value}} {{unit}}. Vui lòng kiểm tra ngay. "
                        + "Nhấn 1 để xác nhận đã nghe, nhấn 2 để chuyển cho chủ nhà, "
                        + "nhấn 9 để tắt cảnh báo gọi.",
                "Emergency alert. Gas concentration has exceeded the critical threshold at "
                        + "{{areaName}}, {{value}} {{unit}}. Please check immediately. "
                        + "Press 1 to acknowledge, press 2 to escalate to your landlord, "
                        + "or press 9 to opt out of voice alerts.",
                "緊急警報。{{areaName}}で検出されたガス濃度が危険な閾値を超えました。"
                        + "{{value}}{{unit}}。直ちに確認してください。"
                        + "確認するには1を、大家さんに転送するには2を、音声通知を無効にするには9を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.FIRE_CRITICAL,
                "Cảnh báo khẩn cấp. Nhiệt độ tại {{areaName}} là {{value}} độ C, vượt ngưỡng an toàn. "
                        + "Nghi ngờ có cháy. Vui lòng kiểm tra ngay. "
                        + "Nhấn 1 để xác nhận, nhấn 2 để chuyển cho chủ nhà.",
                "Emergency alert. Temperature at {{areaName}} is {{value}} degrees Celsius, "
                        + "exceeding the safety threshold. Possible fire. Please check immediately. "
                        + "Press 1 to acknowledge, press 2 to escalate.",
                "緊急警報。{{areaName}}の温度は{{value}}度で、安全閾値を超えています。"
                        + "火災の可能性があります。直ちに確認してください。"
                        + "確認するには1を、転送するには2を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.POWER_LOST,
                "Thông báo. Khu vực {{areaName}} đã mất điện. "
                        + "Nhấn 1 để xác nhận.",
                "Notification. Power has been lost at {{areaName}}. Press 1 to acknowledge.",
                "お知らせ。{{areaName}}で停電が発生しました。確認するには1を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.POWER_RESTORED,
                "Thông báo. Khu vực {{areaName}} đã có điện trở lại.",
                "Notification. Power has been restored at {{areaName}}.",
                "お知らせ。{{areaName}}の電力が復旧しました。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.WATER_LEAK_SUSPECTED,
                "Cảnh báo. Nghi ngờ rò rỉ nước tại {{areaName}}. "
                        + "Dòng nước chảy liên tục {{value}} {{unit}}. "
                        + "Vui lòng kiểm tra. Nhấn 1 để xác nhận, nhấn 2 để chuyển cho chủ nhà.",
                "Warning. Suspected water leak at {{areaName}}. "
                        + "Continuous flow {{value}} {{unit}}. Please check. "
                        + "Press 1 to acknowledge, press 2 to escalate.",
                "警告。{{areaName}}で水漏れの可能性があります。"
                        + "連続流量{{value}}{{unit}}。ご確認ください。"
                        + "確認するには1を、転送するには2を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.GAS_WARNING,
                "Cảnh báo. Nồng độ gas tại {{areaName}} là {{value}} {{unit}}, vượt ngưỡng khuyến nghị. "
                        + "Vui lòng thông gió khu vực. Nhấn 1 để xác nhận.",
                "Warning. Gas concentration at {{areaName}} is {{value}} {{unit}}, "
                        + "above recommended level. Please ventilate. Press 1 to acknowledge.",
                "警告。{{areaName}}のガス濃度は{{value}}{{unit}}で、推奨レベルを超えています。"
                        + "換気してください。確認するには1を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.EIF_ANOMALY_POWER,
                "Thông báo từ hệ thống. Mức tiêu thụ điện bất thường tại {{areaName}}. "
                        + "Vui lòng kiểm tra thiết bị đang sử dụng. Nhấn 1 để xác nhận.",
                "System notification. Abnormal power consumption at {{areaName}}. "
                        + "Please review running appliances. Press 1 to acknowledge.",
                "システム通知。{{areaName}}で異常な電力消費を検出しました。"
                        + "使用中の機器を確認してください。確認するには1を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.EIF_ANOMALY_WATER,
                "Thông báo từ hệ thống. Mức dùng nước bất thường tại {{areaName}}. "
                        + "Vui lòng kiểm tra đường ống. Nhấn 1 để xác nhận.",
                "System notification. Abnormal water usage at {{areaName}}. "
                        + "Please check plumbing. Press 1 to acknowledge.",
                "システム通知。{{areaName}}で異常な水の使用を検出しました。"
                        + "配管を確認してください。確認するには1を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.UTILITY_ELECTRICITY_WARNING,
                "Cảnh báo. Nhà {{houseName}} đã dùng {{usagePercent}} phần trăm hạn mức điện tháng {{month}}, "
                        + "tương đương {{currentUsage}} trên {{monthlyLimit}} {{unit}}. Vui lòng kiểm tra. Nhấn 1 để xác nhận.",
                "Warning. {{houseName}} has used {{usagePercent}} percent of the electricity limit for {{month}}, "
                        + "{{currentUsage}} of {{monthlyLimit}} {{unit}}. Please review. Press 1 to acknowledge.",
                "警告。{{houseName}}の{{month}}電力使用量は上限の{{usagePercent}}パーセント、"
                        + "{{currentUsage}}/{{monthlyLimit}}{{unit}}です。確認するには1を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.UTILITY_WATER_WARNING,
                "Cảnh báo. Nhà {{houseName}} đã dùng {{usagePercent}} phần trăm hạn mức nước tháng {{month}}, "
                        + "tương đương {{currentUsage}} trên {{monthlyLimit}} {{unit}}. Vui lòng kiểm tra. Nhấn 1 để xác nhận.",
                "Warning. {{houseName}} has used {{usagePercent}} percent of the water limit for {{month}}, "
                        + "{{currentUsage}} of {{monthlyLimit}} {{unit}}. Please review. Press 1 to acknowledge.",
                "警告。{{houseName}}の{{month}}水道使用量は上限の{{usagePercent}}パーセント、"
                        + "{{currentUsage}}/{{monthlyLimit}}{{unit}}です。確認するには1を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.UTILITY_ELECTRICITY_CRITICAL,
                "Cảnh báo khẩn cấp. Nhà {{houseName}} đã vượt hạn mức điện tháng {{month}}, "
                        + "{{currentUsage}} trên {{monthlyLimit}} {{unit}}, đạt {{usagePercent}} phần trăm. "
                        + "Vui lòng xử lý ngay. Nhấn 1 để xác nhận, nhấn 2 để chuyển cho chủ nhà.",
                "Critical alert. {{houseName}} has exceeded the electricity limit for {{month}}, "
                        + "{{currentUsage}} of {{monthlyLimit}} {{unit}}, reaching {{usagePercent}} percent. "
                        + "Please act now. Press 1 to acknowledge, press 2 to escalate.",
                "緊急警報。{{houseName}}の{{month}}電力使用量が上限を超えました。"
                        + "{{currentUsage}}/{{monthlyLimit}}{{unit}}、{{usagePercent}}パーセントです。"
                        + "確認するには1を、転送するには2を押してください。"
        );

        seedVoice(templateRepo, versionRepo, AlertEventType.UTILITY_WATER_CRITICAL,
                "Cảnh báo khẩn cấp. Nhà {{houseName}} đã vượt hạn mức nước tháng {{month}}, "
                        + "{{currentUsage}} trên {{monthlyLimit}} {{unit}}, đạt {{usagePercent}} phần trăm. "
                        + "Vui lòng xử lý ngay. Nhấn 1 để xác nhận, nhấn 2 để chuyển cho chủ nhà.",
                "Critical alert. {{houseName}} has exceeded the water limit for {{month}}, "
                        + "{{currentUsage}} of {{monthlyLimit}} {{unit}}, reaching {{usagePercent}} percent. "
                        + "Please act now. Press 1 to acknowledge, press 2 to escalate.",
                "緊急警報。{{houseName}}の{{month}}水道使用量が上限を超えました。"
                        + "{{currentUsage}}/{{monthlyLimit}}{{unit}}、{{usagePercent}}パーセントです。"
                        + "確認するには1を、転送するには2を押してください。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.GAS_CRITICAL,
                "[ISUMS] KHAN CAP: Gas vuot nguong nguy hiem tai {{areaName}} ({{value}} {{unit}}). Hay kiem tra ngay.",
                "[ISUMS] EMERGENCY: Gas at {{areaName}} above critical ({{value}} {{unit}}). Check immediately.",
                "[ISUMS] 緊急: {{areaName}}のガス濃度が危険({{value}}{{unit}})。至急確認を。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.FIRE_CRITICAL,
                "[ISUMS] KHAN CAP: Nhiet do cao tai {{areaName}} ({{value}}C). Nghi co chay.",
                "[ISUMS] EMERGENCY: High temperature at {{areaName}} ({{value}}C). Possible fire.",
                "[ISUMS] 緊急: {{areaName}}高温({{value}}C)。火災の可能性。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.POWER_LOST,
                "[ISUMS] Mat dien tai {{areaName}}.",
                "[ISUMS] Power lost at {{areaName}}.",
                "[ISUMS] {{areaName}}で停電。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.WATER_LEAK_SUSPECTED,
                "[ISUMS] Nghi ro ri nuoc tai {{areaName}} ({{value}} {{unit}}). Kiem tra giup.",
                "[ISUMS] Suspected water leak at {{areaName}} ({{value}} {{unit}}). Please check.",
                "[ISUMS] {{areaName}}水漏れ疑い({{value}}{{unit}})。ご確認を。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.GAS_WARNING,
                "[ISUMS] Canh bao: Gas tai {{areaName}} dat {{value}} {{unit}}. Hay thong gio va kiem tra.",
                "[ISUMS] Warning: Gas at {{areaName}} reached {{value}} {{unit}}. Ventilate and check.",
                "[ISUMS] 警告: {{areaName}}のガス濃度{{value}}{{unit}}。換気して確認してください。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.EIF_ANOMALY_POWER,
                "[ISUMS] Canh bao: Dien nang tieu thu bat thuong tai {{areaName}}. Hay kiem tra thiet bi.",
                "[ISUMS] Warning: Abnormal power usage at {{areaName}}. Please check appliances.",
                "[ISUMS] 警告: {{areaName}}で異常な電力使用。機器を確認してください。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.EIF_ANOMALY_WATER,
                "[ISUMS] Canh bao: Nuoc tieu thu bat thuong tai {{areaName}}. Hay kiem tra duong ong.",
                "[ISUMS] Warning: Abnormal water usage at {{areaName}}. Please check plumbing.",
                "[ISUMS] 警告: {{areaName}}で異常な水使用。配管を確認してください。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.UTILITY_ELECTRICITY_WARNING,
                "[ISUMS] Canh bao dien: {{houseName}} da dung {{usagePercent}}% han muc thang {{month}} ({{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] Electricity warning: {{houseName}} used {{usagePercent}}% of {{month}} limit ({{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] 電力警告: {{houseName}}は{{month}}上限の{{usagePercent}}%を使用({{currentUsage}}/{{monthlyLimit}}{{unit}})。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.UTILITY_WATER_WARNING,
                "[ISUMS] Canh bao nuoc: {{houseName}} da dung {{usagePercent}}% han muc thang {{month}} ({{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] Water warning: {{houseName}} used {{usagePercent}}% of {{month}} limit ({{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] 水道警告: {{houseName}}は{{month}}上限の{{usagePercent}}%を使用({{currentUsage}}/{{monthlyLimit}}{{unit}})。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.UTILITY_ELECTRICITY_CRITICAL,
                "[ISUMS] KHAN CAP dien: {{houseName}} vuot han muc thang {{month}} ({{usagePercent}}%, {{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] CRITICAL electricity: {{houseName}} exceeded {{month}} limit ({{usagePercent}}%, {{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] 緊急 電力: {{houseName}}は{{month}}上限超過({{usagePercent}}%, {{currentUsage}}/{{monthlyLimit}}{{unit}})。"
        );

        seedSms(templateRepo, versionRepo, AlertEventType.UTILITY_WATER_CRITICAL,
                "[ISUMS] KHAN CAP nuoc: {{houseName}} vuot han muc thang {{month}} ({{usagePercent}}%, {{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] CRITICAL water: {{houseName}} exceeded {{month}} limit ({{usagePercent}}%, {{currentUsage}}/{{monthlyLimit}} {{unit}}).",
                "[ISUMS] 緊急 水道: {{houseName}}は{{month}}上限超過({{usagePercent}}%, {{currentUsage}}/{{monthlyLimit}}{{unit}})。"
        );
    }

    private void seedVoice(ChannelTemplateRepository templateRepo,
                            ChannelTemplateVersionRepository versionRepo,
                            AlertEventType event,
                            String viBody, String enBody, String jaBody) {
        String key = "voice_" + event.name().toLowerCase();
        upsertIfAbsent(templateRepo, versionRepo,
                key, NotificationChannel.VOICE, event.name(),
                LocaleType.vi_VN, viBody, null);
        upsertIfAbsent(templateRepo, versionRepo,
                key, NotificationChannel.VOICE, event.name(),
                LocaleType.en_US, enBody, null);
        upsertIfAbsent(templateRepo, versionRepo,
                key, NotificationChannel.VOICE, event.name(),
                LocaleType.ja_JP, jaBody, null);
    }

    private void seedSms(ChannelTemplateRepository templateRepo,
                          ChannelTemplateVersionRepository versionRepo,
                          AlertEventType event,
                          String viBody, String enBody, String jaBody) {
        String key = "sms_" + event.name().toLowerCase();
        upsertIfAbsent(templateRepo, versionRepo,
                key, NotificationChannel.SMS, event.name(),
                LocaleType.vi_VN, viBody, "ISUMS Alert");
        upsertIfAbsent(templateRepo, versionRepo,
                key, NotificationChannel.SMS, event.name(),
                LocaleType.en_US, enBody, "ISUMS Alert");
        upsertIfAbsent(templateRepo, versionRepo,
                key, NotificationChannel.SMS, event.name(),
                LocaleType.ja_JP, jaBody, "ISUMS Alert");
    }

    private void upsertIfAbsent(ChannelTemplateRepository templateRepo,
                                  ChannelTemplateVersionRepository versionRepo,
                                  String templateKey, NotificationChannel channel, String eventType,
                                  LocaleType locale, String body, String title) {
        ChannelTemplate tpl = templateRepo.findByTemplateKeyAndChannel(templateKey, channel)
                .orElseGet(() -> templateRepo.save(
                        ChannelTemplate.builder()
                                .templateKey(templateKey)
                                .channel(channel)
                                .eventType(eventType)
                                .category("ALERT")
                                .recipientType("TENANT_OR_LANDLORD")
                                .createdBy(ACTOR)
                                .updatedBy(ACTOR)
                                .build()));

        Optional<ChannelTemplateVersion> existing = versionRepo
                .findFirstByTemplate_TemplateKeyAndTemplate_ChannelAndLocaleAndStatusOrderByVersionDesc(
                        templateKey, channel, locale, TemplateStatus.ACTIVE);
        if (existing.isPresent()) return;

        ChannelTemplateVersion v1 = ChannelTemplateVersion.builder()
                .template(tpl)
                .locale(locale)
                .version(1)
                .status(TemplateStatus.ACTIVE)
                .body(body)
                .title(title)
                .allowedVars(ALLOWED_VARS)
                .createdBy(ACTOR)
                .updatedBy(ACTOR)
                .build();
        versionRepo.save(v1);
        log.info("[VoiceAlertSeed] seeded {} channel={} locale={}", templateKey, channel, locale);
    }
}
