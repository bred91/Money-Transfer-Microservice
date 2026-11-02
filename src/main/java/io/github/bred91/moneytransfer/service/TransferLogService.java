package io.github.bred91.moneytransfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.bred91.moneytransfer.entity.TransferLogEntity;
import io.github.bred91.moneytransfer.enumeration.TransactionStatusEnum;
import io.github.bred91.moneytransfer.repository.TransferLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransferLogService {

    private final TransferLogRepository transferLogRepository;

    public boolean isAlreadyPresent(String transactionId) {
        return transferLogRepository.findByTransactionId(transactionId).isPresent();
    }

    public TransferLogEntity saveReceived(String transactionId, UUID fromId, UUID toId, BigDecimal amount) {
        return transferLogRepository.save(TransferLogEntity.builder()
                .transactionId(transactionId)
                .fromAccountId(fromId)
                .toAccountId(toId)
                .amount(amount)
                .status(TransactionStatusEnum.RECEIVED)
                .build());
    }

    public void markFailed(TransferLogEntity logEntry, String message) {
        logEntry.setStatus(TransactionStatusEnum.FAILED);
        logEntry.setFailedReason(message);
        transferLogRepository.save(logEntry);
    }

    public void markSuccess(TransferLogEntity logEntry) {
        logEntry.setStatus(TransactionStatusEnum.SUCCESS);
        transferLogRepository.save(logEntry);
    }
}
