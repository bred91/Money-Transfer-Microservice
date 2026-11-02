package io.github.bred91.moneytransfer.kafka.listener;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.bred91.moneytransfer.dto.TransferEventDTO;
import io.github.bred91.moneytransfer.service.TransferProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventListener {

    private final TransferProcessingService processingService;

    @KafkaListener(topics = "${app.kafka.topic.transfer}", groupId = "${spring.application.name}")
    public void onMessage(TransferEventDTO payload) {

        // Skip null payloads
        if (payload == null) {
            log.error("Received null payload (tombstone record), skipping processing");
            return;
        }

        // Skip invalid transfers
        if (!isValidTransaction(payload)) {
            return;
        }

        try {
            processingService.processTransfer(
                    payload.getTransactionId(),
                    payload.getFromAccountId(),
                    payload.getToAccountId(),
                    payload.getAmount()
            );
        } catch (Exception e) {
            log.error("Failed to process transfer event", e);
            // rethrow to trigger Kafka retry mechanism
            throw new RuntimeException(e);
        }
    }

    private boolean isValidTransaction(TransferEventDTO payload) {

        // Check for null values
        if (StringUtils.isBlank(payload.getTransactionId()) || payload.getFromAccountId() == null
                || payload.getToAccountId() == null || payload.getAmount() == null) {
            log.error("Invalid transfer: missing required fields for transaction with ID {}, from account ID {}, to account ID {} and amount {}",
                    payload.getTransactionId(), payload.getFromAccountId(), payload.getToAccountId(), payload.getAmount());
            return false;
        }

        // Check if amount is a positive value
        if (BigDecimal.ZERO.compareTo(payload.getAmount()) >= 0) {
            log.error("Invalid transfer: amount must be a positive value for transaction {}", payload.getTransactionId());
            return false;
        }

        // Check if source and destination accounts are the same
        if (payload.getFromAccountId().equals(payload.getToAccountId())) {
            log.error("Invalid transfer: source and destination accounts are the same for transaction {}", payload.getTransactionId());
            return false;
        }
        return true;
    }
}

