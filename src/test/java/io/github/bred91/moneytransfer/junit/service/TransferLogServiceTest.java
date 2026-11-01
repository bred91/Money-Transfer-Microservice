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
    void saveReceived_shouldPopulateFieldsWithReceivedStatusAndReturnSavedEntity() {
        when(transferLogRepository.save(any(TransferLogEntity.class))).thenAnswer(invocation -> {
            TransferLogEntity e = invocation.getArgument(0);
            e.setId(id(42));
            return e;
        });

        TransferLogEntity saved = transferLogService.saveReceived("tx-save", id(1), id(2), new BigDecimal("99.99"));

        ArgumentCaptor<TransferLogEntity> captor = ArgumentCaptor.forClass(TransferLogEntity.class);
        verify(transferLogRepository).save(captor.capture());

        TransferLogEntity toSave = captor.getValue();
        assertThat(toSave.getTransactionId()).isEqualTo("tx-save");
        assertThat(toSave.getFromAccountId()).isEqualTo(id(1));
        assertThat(toSave.getToAccountId()).isEqualTo(id(2));
        assertThat(toSave.getAmount()).isEqualByComparingTo("99.99");
        assertThat(toSave.getStatus()).isEqualTo(TransactionStatusEnum.RECEIVED);

        assertThat(saved.getId()).isEqualTo(id(42));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatusEnum.RECEIVED);
    }

    @Test
    void markFailed_shouldSetStatusFailedAndSaveWithReason() {
        TransferLogEntity entry = TransferLogEntity.builder()
                .transactionId("tx-fail")
                .status(TransactionStatusEnum.RECEIVED)
                .build();

        when(transferLogRepository.save(any(TransferLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transferLogService.markFailed(entry, "insufficient funds");

        ArgumentCaptor<TransferLogEntity> captor = ArgumentCaptor.forClass(TransferLogEntity.class);
        verify(transferLogRepository).save(captor.capture());

        TransferLogEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatusEnum.FAILED);
        assertThat(saved.getFailedReason()).isEqualTo("insufficient funds");

        // original instance should also be updated
        assertThat(entry.getStatus()).isEqualTo(TransactionStatusEnum.FAILED);
        assertThat(entry.getFailedReason()).isEqualTo("insufficient funds");
    }

    @Test
    void markSuccess_shouldSetStatusSuccessAndSave() {
        TransferLogEntity entry = TransferLogEntity.builder()
                .transactionId("tx-success")
                .status(TransactionStatusEnum.RECEIVED)
                .build();

        when(transferLogRepository.save(any(TransferLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transferLogService.markSuccess(entry);

        ArgumentCaptor<TransferLogEntity> captor = ArgumentCaptor.forClass(TransferLogEntity.class);
        verify(transferLogRepository).save(captor.capture());

        TransferLogEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatusEnum.SUCCESS);

        // original instance should also be updated
        assertThat(entry.getStatus()).isEqualTo(TransactionStatusEnum.SUCCESS);
    }
}
