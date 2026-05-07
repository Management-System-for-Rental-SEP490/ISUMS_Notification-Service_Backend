package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.entities.SubscriptionPlan;
import com.isums.notificationservice.domains.events.PaymentSubscriptionActivatedEvent;
import com.isums.notificationservice.infrastructures.repositories.SubscriptionPlanRepository;
import com.isums.notificationservice.services.NotificationSubscriptionService;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Activates PREMIUM + resets quotas when Payment-Service publishes a
 * successful subscription charge. Acts as the production path; admins
 * can also use {@code /api/notifications/subscriptions/admin/grant-premium}
 * to skip the payment loop during thesis demos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSubscriptionListener {

    private final NotificationSubscriptionService subscriptionService;
    private final SubscriptionPlanRepository planRepo;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment.subscription-activated",
            groupId = "notification-subscription-group")
    public void onActivated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);

        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            PaymentSubscriptionActivatedEvent event =
                    objectMapper.readValue(record.value(), PaymentSubscriptionActivatedEvent.class);

            if (event.userId() == null) {
                log.warn("[SubscriptionActivated] missing userId messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            // Defensive defaults: Payment-Service guarantees durationDays in
            // the new shape, but a redelivery from before the schema change
            // could still be in flight (older Kafka offsets). Fall back to
            // 30 days so an empty / legacy payload still grants something
            // reasonable instead of silently no-oping the activation.
            int days = event.durationDays() != null && event.durationDays() > 0
                    ? event.durationDays()
                    : 30;

            // Plan-driven quotas: PREMIUM_1M = 100 voice / 200 SMS, while
            // TRIAL_7D ships with smaller caps. Without this lookup the
            // user would land on the legacy TierQuotaPolicy floor (20/30)
            // regardless of which plan they paid for. PlanId comes through
            // as a string in the Map<String,Object> Kafka payload — parse
            // best-effort and fall back to the policy default if missing.
            int voiceQuota = -1;
            int smsQuota   = -1;
            String planIdStr = event.planId();
            if (planIdStr != null && !planIdStr.isBlank()) {
                try {
                    SubscriptionPlan plan = planRepo.findById(UUID.fromString(planIdStr)).orElse(null);
                    if (plan != null) {
                        voiceQuota = plan.getVoiceQuotaMonthly();
                        smsQuota   = plan.getSmsQuotaMonthly();
                    }
                } catch (IllegalArgumentException ex) {
                    log.warn("[SubscriptionActivated] invalid planId={} messageId={} — falling back to tier policy",
                            planIdStr, messageId);
                }
            }

            if (voiceQuota >= 0 && smsQuota >= 0) {
                subscriptionService.activatePremiumByDays(event.userId(), days, voiceQuota, smsQuota);
            } else {
                // Legacy / missing plan path — service uses TierQuotaPolicy.
                subscriptionService.activatePremiumByDays(event.userId(), days);
            }
            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[SubscriptionActivated] user={} plan={} days={} voice={} sms={} txnRef={}",
                    event.userId(), event.planCode(), days, voiceQuota, smsQuota, event.txnRef());
        } catch (Exception e) {
            log.error("[SubscriptionActivated] failed messageId={}: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }
}
