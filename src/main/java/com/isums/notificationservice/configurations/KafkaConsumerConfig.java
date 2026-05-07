package com.isums.notificationservice.configurations;

import com.isums.notificationservice.infrastructures.exceptions.PermanentEventFailureException;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * KafkaTemplate&lt;String, Object&gt; for components that publish typed
     * Java events ({@link com.isums.notificationservice.infrastructures.kafka.NotificationTranslationRequester}).
     * Uses Spring Kafka's JsonSerializer so any record/POJO is serialised to
     * JSON on send — matches the consumer side which deserialises with
     * Jackson into the appropriate event class.
     */
    @Bean
    public KafkaTemplate<String, Object> objectKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate() {
        // All upstream consumers use StringDeserializer so ConsumerRecord.value()
        // arrives as String. The DLT producer previously used ByteArraySerializer
        // which choked with a ClassCastException (String → byte[]) whenever
        // DeadLetterPublishingRecoverer tried to republish — causing the record
        // to loop on retry instead of landing in the DLT. StringSerializer mirrors
        // the consumer side so DLT publication lines up.
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> dltKafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        handler.addNotRetryableExceptions(
                tools.jackson.core.JacksonException.class,
                tools.jackson.databind.exc.InvalidDefinitionException.class,
                tools.jackson.databind.exc.UnrecognizedPropertyException.class,
                IllegalArgumentException.class,
                org.springframework.messaging.converter.MessageConversionException.class,
                PermanentEventFailureException.class
        );

        return handler;
    }
}