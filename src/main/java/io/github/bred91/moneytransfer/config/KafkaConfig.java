package io.github.bred91.moneytransfer.config;

import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conversion, validation and error handling of the consumed transfer events.
 * <p>
 * Record values are consumed as raw bytes and converted to the listener payload type, so that any record
 * that cannot be processed is forwarded unchanged to the dead-letter topic:
 * <ul>
 *     <li>malformed or invalid events (conversion or validation failures) are never retried
 *     and are dead-lettered immediately;</li>
 *     <li>any other failure (for example the database being unavailable) is retried with exponential back-off,
 *     and the event is dead-lettered once the retries are exhausted.</li>
 * </ul>
 * The offset of a record is committed only once it has been processed or dead-lettered, so no event is lost.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TransferKafkaProperties.class)
public class KafkaConfig {

    @Bean
    RecordMessageConverter transferEventMessageConverter(JsonMapper jsonMapper) {
        return new ByteArrayJacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * Applies Bean Validation to {@code @Valid} listener payloads.
     */
    @Bean
    KafkaListenerConfigurer kafkaListenerValidation(Validator validator) {
        return registrar -> registrar.setValidator(new SpringValidatorAdapter(validator));
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaTemplate, TransferKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = getDeadLetterPublishingRecoverer(kafkaTemplate, properties);

        TransferKafkaProperties.Retry retry = properties.retry();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());

        return new DefaultErrorHandler(recoverer, backOff);
    }

    private DeadLetterPublishingRecoverer getDeadLetterPublishingRecoverer(KafkaOperations<?, ?> kafkaTemplate, TransferKafkaProperties properties) {
        String deadLetterTopic = properties.topic().transferDeadLetter();
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> {
            log.error("Sending record {} to dead-letter topic {}", KafkaUtils.format(record), deadLetterTopic, ex);
            // A negative partition lets the producer choose it, so the dead-letter topic
            // does not need as many partitions as the source topic
            return new TopicPartition(deadLetterTopic, -1);
        });
        return recoverer;
    }
}
