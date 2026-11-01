package io.github.bred91.moneytransfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.bred91.moneytransfer.entity.AccountEntity;
import io.github.bred91.moneytransfer.entity.TransferLogEntity;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferProcessingService {

    private final TransferLogService transferLogService;
    private final AccountRepository accountRepository;

    @Transactional(timeout = 10)
    public void processTransfer(String transactionId, UUID fromId, UUID toId, BigDecimal amount) {

        // Idempotency check
        if (transferLogService.isAlreadyPresent(transactionId)) {
            log.warn("Transfer with transaction ID {} already processed, skipping", transactionId);
            return;
        }

        TransferLogEntity logEntry = transferLogService.saveReceived(transactionId, fromId, toId, amount);

        // Lock (pessimistically) both accounts.
        // To avoid deadlocks, the database acquires the locks in a consistent order (by id).
        // Java and PostgreSQL order UUIDs differently, so accounts are matched by id, not by position.
        List<AccountEntity> accounts = accountRepository.findAllByIdForUpdateOrdered(Set.of(fromId, toId));

        // Check if both accounts exist
        if (accounts.size() != 2) {
            log.error("One or both accounts not found for transaction {}", transactionId);
            transferLogService.markFailed(logEntry, "One or both accounts not found");
            return;
        }

        AccountEntity from = accounts.get(0).getId().equals(fromId) ? accounts.get(0) : accounts.get(1);
        AccountEntity to = from == accounts.get(0) ? accounts.get(1) : accounts.get(0);

        // Check if source account is active
        if (!AccountStatusEnum.ACTIVE.equals(from.getStatus())) {
            log.warn("Source account {} is not active for transaction {}", fromId, transactionId);
            transferLogService.markFailed(logEntry, "Source account is not active");
            return;
        }
        // Check if destination account is active
        if (!AccountStatusEnum.ACTIVE.equals(to.getStatus())) {
            log.warn("Destination account {} is not active for transaction {}", toId, transactionId);
            transferLogService.markFailed(logEntry, "Destination account is not active");
            return;
        }

        // Check if source account has sufficient funds
        if (from.getBalance().compareTo(amount) < 0) {
            log.error("Insufficient funds for transaction {}", transactionId);
            transferLogService.markFailed(logEntry, "Insufficient funds");
            return;
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepository.saveAll(List.of(from, to));

        transferLogService.markSuccess(logEntry);

        log.info("Processed transfer {} from {} to {} amount {}", transactionId, fromId, toId, amount);
    }
}

