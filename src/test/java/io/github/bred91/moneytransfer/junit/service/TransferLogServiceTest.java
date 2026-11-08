package io.github.bred91.moneytransfer.junit.service;

import static io.github.bred91.moneytransfer.TestIds.id;

import io.github.bred91.moneytransfer.entity.TransferLogEntity;
import io.github.bred91.moneytransfer.enumeration.TransactionStatusEnum;
import io.github.bred91.moneytransfer.repository.TransferLogRepository;
import io.github.bred91.moneytransfer.service.TransferLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferLogServiceTest {
    @Mock
    private TransferLogRepository transferLogRepository;

    @InjectMocks
    private TransferLogService transferLogService;

    @Test
    void isAlreadyPresent_returnsTrueWhenRepositoryContainsTransaction() {
        when(transferLogRepository.findByTransactionId("tx-123")).thenReturn(Optional.of(new TransferLogEntity()));

        boolean result = transferLogService.isAlreadyPresent("tx-123");

        assertThat(result).isTrue();
        verify(transferLogRepository).findByTransactionId("tx-123");
    }

    @Test
    void isAlreadyPresent_returnsFalseWhenRepositoryDoesNotContainTransaction() {
        when(transferLogRepository.findByTransactionId("missing-tx")).thenReturn(Optional.empty());

        boolean result = transferLogService.isAlreadyPresent("missing-tx");

        assertThat(result).isFalse();
        verify(transferLogRepository).findByTransactionId("missing-tx");
    }

    @Test
    void recordSuccess_shouldSaveLogWithSuccessStatusAndNoReason() {
        transferLogService.recordSuccess("tx-success", id(1), id(2), new BigDecimal("99.99"));

        TransferLogEntity saved = savedLog();
        assertThat(saved.getTransactionId()).isEqualTo("tx-success");
        assertThat(saved.getFromAccountId()).isEqualTo(id(1));
        assertThat(saved.getToAccountId()).isEqualTo(id(2));
        assertThat(saved.getAmount()).isEqualByComparingTo("99.99");
        assertThat(saved.getStatus()).isEqualTo(TransactionStatusEnum.SUCCESS);
        assertThat(saved.getFailedReason()).isNull();
    }

    @Test
    void recordFailure_shouldSaveLogWithFailedStatusAndReason() {
        transferLogService.recordFailure("tx-fail", id(1), id(2), new BigDecimal("10.00"), "Insufficient funds");

        TransferLogEntity saved = savedLog();
        assertThat(saved.getTransactionId()).isEqualTo("tx-fail");
        assertThat(saved.getFromAccountId()).isEqualTo(id(1));
        assertThat(saved.getToAccountId()).isEqualTo(id(2));
        assertThat(saved.getAmount()).isEqualByComparingTo("10.00");
        assertThat(saved.getStatus()).isEqualTo(TransactionStatusEnum.FAILED);
        assertThat(saved.getFailedReason()).isEqualTo("Insufficient funds");
    }

    private TransferLogEntity savedLog() {
        ArgumentCaptor<TransferLogEntity> captor = ArgumentCaptor.forClass(TransferLogEntity.class);
        verify(transferLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
