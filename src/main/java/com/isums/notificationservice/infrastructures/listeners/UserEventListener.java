package com.isums.notificationservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.isums.notificationservice.domains.dtos.SendEmailEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
            emailService.sendEmail(event.to(), event.templateCode().toLowerCase(), LocaleType.vi_VN, event.params());
            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
        } catch (JacksonException e) {
            log.error("Deserialization failed raw={}", record.value(), e);
            ack.acknowledge();
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Processing failed, will retry", e);
            throw e;
        } finally {
            kafkaHelper.clearMDC();
        }
    }
}
