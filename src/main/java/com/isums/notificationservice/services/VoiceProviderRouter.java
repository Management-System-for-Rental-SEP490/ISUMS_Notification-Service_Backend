package com.isums.notificationservice.services;

import com.isums.notificationservice.infrastructures.abstracts.VoiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class VoiceProviderRouter {

    private final List<VoiceProvider> providers;
    private final NotificationRuntimeSettingService settingService;

    @Value("${app.notification.voice.provider:STRINGEE}")
    private String fallbackProvider;

    public VoiceProviderRouter(List<VoiceProvider> providers,
                                NotificationRuntimeSettingService settingService) {
        this.providers = providers;
        this.settingService = settingService;
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("[VoiceProviderRouter] fallback={} available={}",
                fallbackProvider,
                providers.stream().map(VoiceProvider::providerId).toList());
    }

    public VoiceProvider voice() {
        return resolve(activeProviderId());
    }

    public VoiceProvider sms() {
        return resolve(activeProviderId());
    }

    public List<String> availableProviderIds() {
        return providers.stream().map(VoiceProvider::providerId).toList();
    }

    public String activeProviderId() {
        return settingService.getOrDefault(
                NotificationRuntimeSettingService.KEY_VOICE_PROVIDER, fallbackProvider);
    }

    private VoiceProvider resolve(String id) {
        return providers.stream()
                .filter(p -> p.providerId().equalsIgnoreCase(id))
                .findFirst()
                .orElseGet(() -> {
                    if (providers.isEmpty()) {
                        throw new IllegalStateException(
                                "No VoiceProvider beans on the classpath — check StringeeClientImpl @Service");
                    }
                    log.warn("[VoiceProviderRouter] unknown provider {} — using {}",
                            id, providers.get(0).providerId());
                    return providers.get(0);
                });
    }
}
