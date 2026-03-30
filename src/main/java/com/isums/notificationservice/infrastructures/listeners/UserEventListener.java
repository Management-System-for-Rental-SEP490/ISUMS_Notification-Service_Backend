package com.isums.notificationservice.infrastructures.listeners;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.isums.notificationservice.domains.events.SendEmailEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final EmailService emailService;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "notification-email", groupId = "notification-group", concurrency = "3")
    public void handleSendEmail(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);

        try {
            if (idempotencyService.isDuplicate(messageId)) {
                log.warn("Duplicate skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            SendEmailEvent event = objectMapper.readValue(record.value(), SendEmailEvent.class);

            if (event.to() == null || event.to().isBlank()) {
                log.error("Invalid event: missing 'to' field. messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            emailService.sendEmail(
                    event.to(),
                    event.templateCode().toLowerCase(),
                    LocaleType.vi_VN,
                    event.params()
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("email_dispatched messageId={} to={} template={}", messageId, event.to(), event.templateCode());

        } catch (JacksonException e) {
            log.error("Deserialization failed messageId={} raw={}: {}", messageId, record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Processing failed messageId={}, will retry: {}", messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }
}