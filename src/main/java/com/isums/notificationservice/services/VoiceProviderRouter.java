package com.isums.notificationservice.services;

import com.isums.notificationservice.infrastructures.abstracts.VoiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Picks a {@link VoiceProvider} based on
 * {@code app.notification.voice.provider} (default {@code STRINGEE}).
 * Stringee handles BOTH voice and SMS now — provider abstraction is
 * kept so swapping vendors later only touches this router.
 *
 * <p>Routing is per-request, not per-bean — flip the property without
 * a restart and the next dispatch picks up the new provider.
 */
@Component
@Slf4j
public class VoiceProviderRouter {

    private final List<VoiceProvider> providers;

    @Value("${app.notification.voice.provider:STRINGEE}")
    private String defaultProvider;

    public VoiceProviderRouter(List<VoiceProvider> providers) {
        this.providers = providers;
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("[VoiceProviderRouter] default={} available={}",
                defaultProvider,
                providers.stream().map(VoiceProvider::providerId).toList());
    }

    /** The voice provider for outbound TTS calls. */
    public VoiceProvider voice() {
        return resolve(defaultProvider);
    }

    /** The SMS provider — currently the same Stringee bean. */
    public VoiceProvider sms() {
        return resolve(defaultProvider);
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
