package io.github.bred91.moneytransfer.kafka.listener;

import io.github.bred91.moneytransfer.dto.TransferEventDTO;
import io.github.bred91.moneytransfer.service.TransferProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes transfer events and delegates them to {@link TransferProcessingService}.
 * <p>
 * Payloads are converted and validated before this method is invoked: malformed or invalid events never reach it
 * and are dead-lettered, while exceptions thrown by the processing are retried by the container error handler
 * (see {@link io.github.bred91.moneytransfer.config.KafkaConfig}).
 * The payload is required, so records without a value (tombstones) are dead-lettered too.
 */
@Component
@RequiredArgsConstructor
public class TransferEventListener {

    private final TransferProcessingService processingService;

    @KafkaListener(topics = "${app.kafka.topic.transfer}")
    public void onMessage(@Payload @Valid TransferEventDTO payload) {
        processingService.processTransfer(
                payload.getTransactionId(),
                payload.getFromAccountId(),
                payload.getToAccountId(),
                payload.getAmount()
        );
    }
}
